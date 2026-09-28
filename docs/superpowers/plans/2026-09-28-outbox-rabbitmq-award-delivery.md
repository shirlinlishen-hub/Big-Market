# Outbox、RabbitMQ 与积分奖品异步发放 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已经落库的 `AWARD_DELIVERY_REQUESTED` Outbox 可靠发布到 RabbitMQ，由具备 Inbox、任务状态和积分流水三层幂等的消费者完成积分发放，并补齐死信、人工重试和运行模式切换。

**Architecture:** 抽奖事务继续只写 MySQL 发放任务和 Outbox。独立发布任务通过带令牌的短租约领取 Outbox，等待 RabbitMQ Publisher Confirm 后再修改发布状态。消费者使用 RabbitMQ 至少一次投递，在一个 MySQL 事务内登记 Inbox、锁定发放任务、执行奖品处理器、写积分流水与账户并更新任务；秒级失败使用带随机抖动的指数退避，永久错误和耗尽重试的消息进入死信。

**Tech Stack:** Java 21、Spring Boot 3.4.3、Spring AMQP、Spring Retry、MyBatis、MySQL 8、RabbitMQ、JUnit 5、Mockito

**Spec:** [2026-09-28-outbox-rabbitmq-award-delivery-design.md](../specs/2026-09-28-outbox-rabbitmq-award-delivery-design.md)

## Global Constraints

- MySQL 仍是发放任务、Inbox、Outbox、积分账户和积分流水的权威数据源。
- 本阶段只发布 `AWARD_DELIVERY_REQUESTED`，不能将其他无消费者的 Outbox 标记为已发布。
- 消息只携带 `orderId` 和 `userId` 等定位数据；积分数值从 `award_delivery_task.award_value` 读取。
- RabbitMQ 提供至少一次投递，业务侧通过 Inbox、任务状态和 `user_points_ledger.order_id` 保证重复消息不重复加积分。
- 消费端第一次立即执行，失败后使用约 1、2、4、8 秒的随机化指数退避，最多尝试 5 次；永久错误不重试。
- 不允许用分钟级睡眠占用监听线程。需要长时间等待时进入死信并通过人工重试生成新事件。
- Outbox 发布失败使用数据库 `next_attempt_at` 调度，间隔为 `min(2^attempts, 3600)` 秒，10 次失败后进入 DEAD。
- `delivery.mode=local|mq-prepare|mq` 必须保证正式运行时只有一个发放入口。
- 迁移采用新增字段和新表，保留现有本地任务作为灰度与应急回退能力。
- 不引入分库分表、外部券供应商、实物履约系统和通用事件总线。
- 开始每个任务前记录目标文件的现有 diff；同一文件已有修改时只暂存本任务新增的代码块，未跟踪的基础文件需要完整纳入时在提交说明中明确记录，禁止清理或回退现有工作区内容。

## Review Focus

1. RabbitMQ 已接收消息但 Outbox 标记失败时，租约到期后的重复发布是否只产生一次积分结果。
2. 消费事务已提交但 ACK 丢失时，Inbox 和积分流水是否都能安全跳过重放。
3. 相同 `eventId` 携带不同 `eventKey` 或摘要时，是否直接作为协议冲突进入死信。
4. 租约被新发布器接管后，旧 `lockToken` 是否无法标记发布成功或覆盖失败结果。
5. 人工重试生成不同 `eventId` 后，`orderId` 唯一积分流水是否仍能阻止重复入账。

---

## Phase 1：数据库契约与持久化

### Task 1：增加 Outbox 租约、Inbox 和发放版本迁移

**Files:**

- Create: `docs/dev-ops/mysql/sql/2026-09-28-outbox-rabbitmq-award-delivery.sql`
- Modify: `docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md`

**Interfaces:**

- `outbox_event.status`: `0=PENDING, 1=PUBLISHED, 2=DEAD`
- `inbox_event` 主键：`(consumer_name, event_id)`
- `award_delivery_task.dispatch_version`: 人工重新投递版本

- [ ] **Step 1: 编写幂等迁移脚本**

迁移包含以下结构，并在执行前用 `information_schema` 或迁移工具保证重复执行不会重复加字段：

```sql
ALTER TABLE outbox_event
    ADD COLUMN locked_by varchar(64) DEFAULT NULL,
    ADD COLUMN lock_token varchar(64) DEFAULT NULL,
    ADD COLUMN locked_until datetime DEFAULT NULL,
    ADD COLUMN published_time datetime DEFAULT NULL,
    ADD COLUMN dead_time datetime DEFAULT NULL,
    ADD KEY idx_outbox_claim
        (event_type, status, next_attempt_at, locked_until, create_time);

CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name varchar(64) NOT NULL,
    event_id varchar(64) NOT NULL,
    event_key varchar(128) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    payload_hash char(64) NOT NULL,
    status tinyint NOT NULL DEFAULT 0,
    duplicate_count int NOT NULL DEFAULT 0,
    first_received_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_received_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_time datetime DEFAULT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_event_key (event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE award_delivery_task
    ADD COLUMN dispatch_version int NOT NULL DEFAULT 0;
```

- [ ] **Step 2: 增加历史待发放任务补写语句**

补写只针对没有初始事件的 PENDING 任务，事件键固定为 `award-delivery:{orderId}:v0`。`INSERT ... SELECT ... WHERE NOT EXISTS` 和 `uk_outbox_event_key` 共同保证脚本可重入。

- [ ] **Step 3: 在迁移说明中记录执行顺序与校验 SQL**

校验必须覆盖重复事件键、无对应任务的事件、重复积分流水以及 Outbox 各状态数量。

- [ ] **Step 4: 静态验证 SQL**

Run:

```powershell
rg -n "inbox_event|dispatch_version|idx_outbox_claim|AWARD_DELIVERY_REQUESTED" docs/dev-ops/mysql/sql/2026-09-28-outbox-rabbitmq-award-delivery.sql
git diff --check -- docs/dev-ops/mysql/sql/2026-09-28-outbox-rabbitmq-award-delivery.sql docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md
```

Expected: 四类结构均能检索到，`git diff --check` 无输出。

- [ ] **Step 5: Commit**

```powershell
git add -- docs/dev-ops/mysql/sql/2026-09-28-outbox-rabbitmq-award-delivery.sql docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md
git commit --only -m "feat: add reliable delivery database schema" -- docs/dev-ops/mysql/sql/2026-09-28-outbox-rabbitmq-award-delivery.sql docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md
```

### Task 2：扩展 Outbox、Inbox、任务和积分流水 Mapper

**Files:**

- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/OutboxEvent.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/InboxEvent.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/UserPointsLedger.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/AwardDeliveryTask.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/model/entity/AwardDeliveryTaskEntity.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IOutboxEventDao.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IInboxEventDao.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IUserPointsDao.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IAwardDeliveryTaskDao.java`
- Modify: `Big-Market-app/src/main/resources/mybatis/mapper/OutboxEventMapper.xml`
- Create: `Big-Market-app/src/main/resources/mybatis/mapper/InboxEventMapper.xml`
- Modify: `Big-Market-app/src/main/resources/mybatis/mapper/UserPointsMapper.xml`
- Modify: `Big-Market-app/src/main/resources/mybatis/mapper/AwardDeliveryTaskMapper.xml`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Infrastructure/ReliableDeliveryMapperContractTest.java`

**Interfaces:**

```java
public interface IOutboxEventDao {
    List<OutboxEvent> selectClaimable(int limit);
    int acquireLease(String eventId, String lockedBy, String lockToken, Date lockedUntil);
    int markPublished(String eventId, String lockToken);
    int recordPublishFailure(String eventId, String lockToken, String reason);
}

public interface IInboxEventDao {
    int insertOrTouch(InboxEvent event);
    InboxEvent selectForUpdate(String consumerName, String eventId);
    int markSuccess(String consumerName, String eventId);
}

public interface IUserPointsDao {
    UserPointsLedger selectLedgerForUpdate(String orderId);
    int insertLedger(String orderId, String userId, int points);
    int addPoints(String userId, int points);
}
```

- [ ] **Step 1: 写失败的 Mapper 契约测试**

测试解析全部 Mapper XML，并断言：

- Outbox 领取 SQL 含 `FOR UPDATE SKIP LOCKED` 和事件类型过滤。
- 发布成功与失败 SQL 都含 `event_id`、`status=0` 和 `lock_token` 条件。
- Inbox 使用复合主键，重复写只增加 `duplicate_count`。
- 积分流水不再使用 `INSERT IGNORE`，并存在 `selectLedgerForUpdate`。
- 任务查询映射 `dispatch_version`。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=ReliableDeliveryMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，缺少 Inbox Mapper 和新方法/字段。

- [ ] **Step 3: 实现 PO、DAO 和 SQL**

Outbox 领取只返回下列事件并按创建时间排序：

```sql
WHERE event_type = 'AWARD_DELIVERY_REQUESTED'
  AND status = 0
  AND next_attempt_at <= NOW()
  AND (locked_until IS NULL OR locked_until < NOW())
ORDER BY create_time, event_id
LIMIT #{limit}
FOR UPDATE SKIP LOCKED
```

失败更新在第 10 次时设置 `status=2, dead_time=NOW()`，其余记录计算下次时间，并在两种结果中清空租约字段。

- [ ] **Step 4: 运行契约测试**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 5: Commit**

```powershell
$paths = @(
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/OutboxEvent.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/InboxEvent.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/UserPointsLedger.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/po/AwardDeliveryTask.java',
  'Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/model/entity/AwardDeliveryTaskEntity.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IOutboxEventDao.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IInboxEventDao.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IUserPointsDao.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IAwardDeliveryTaskDao.java',
  'Big-Market-app/src/main/resources/mybatis/mapper/OutboxEventMapper.xml',
  'Big-Market-app/src/main/resources/mybatis/mapper/InboxEventMapper.xml',
  'Big-Market-app/src/main/resources/mybatis/mapper/UserPointsMapper.xml',
  'Big-Market-app/src/main/resources/mybatis/mapper/AwardDeliveryTaskMapper.xml',
  'Big-Market-app/src/test/java/shirlin/ai/test/Infrastructure/ReliableDeliveryMapperContractTest.java'
)
git add -- $paths
git commit --only -m "feat: add outbox lease and inbox persistence" -- $paths
```

---

## Phase 2：Outbox 可靠发布

### Task 3：实现短事务 Outbox 领取服务

**Files:**

- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxClaimService.java`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Infrastructure/OutboxClaimServiceTest.java`

**Interfaces:**

```java
@Transactional
public List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration lease);
```

- [ ] **Step 1: 写领取行为测试**

覆盖批次上限 1 至 500、空批次、同批共享 UUID `lockToken`、30 秒租约，以及 `acquireLease` 返回 0 时从结果中过滤该事件。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=OutboxClaimServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，`OutboxClaimService` 尚不存在。

- [ ] **Step 3: 实现短事务领取**

核心实现保持数据库事务内无网络调用：

```java
@Transactional(rollbackFor = Exception.class)
public List<OutboxEvent> claimBatch(String workerId, int batchSize, Duration lease) {
    int limit = Math.max(1, Math.min(batchSize, 500));
    String token = UUID.randomUUID().toString();
    Date until = Date.from(Instant.now().plus(lease));
    return eventDao.selectClaimable(limit).stream()
            .filter(event -> eventDao.acquireLease(
                    event.getEventId(), workerId, token, until) == 1)
            .peek(event -> {
                event.setLockedBy(workerId);
                event.setLockToken(token);
                event.setLockedUntil(until);
            }).toList();
}
```

- [ ] **Step 4: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 5: Commit**

```powershell
git add -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxClaimService.java Big-Market-app/src/test/java/shirlin/ai/test/Infrastructure/OutboxClaimServiceTest.java
git commit --only -m "feat: claim outbox events with leases" -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxClaimService.java Big-Market-app/src/test/java/shirlin/ai/test/Infrastructure/OutboxClaimServiceTest.java
```

### Task 4：增加 RabbitMQ 拓扑、消息信封和 Publisher Confirm

**Files:**

- Modify: `Big-Market-app/pom.xml`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/model/DomainEventEnvelope.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/model/AwardDeliveryRequestedPayload.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/IMessagePublisher.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/config/RabbitMQConfig.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/messaging/RabbitMessagePublisher.java`
- Modify: `Big-Market-app/src/main/resources/application.yml`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Messaging/RabbitMessagePublisherTest.java`

**Interfaces:**

```java
public record DomainEventEnvelope<T>(
        String eventId, String eventType, String eventKey, int schemaVersion,
        String aggregateId, String partitionKey, Instant occurredAt, T payload) {}

public interface IMessagePublisher {
    void publish(DomainEventEnvelope<AwardDeliveryRequestedPayload> event);
}
```

- [ ] **Step 1: 加入 AMQP 与 Retry 依赖并写发布测试**

`Big-Market-app/pom.xml` 增加 `spring-boot-starter-amqp` 和 `spring-retry`。测试验证交换机、路由键、持久化消息、`messageId=eventId`、`correlationId=aggregateId`，并覆盖 ACK、NACK、Return 和 Confirm 超时。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=RabbitMessagePublisherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，消息类和发布器尚不存在。

- [ ] **Step 3: 声明固定拓扑**

```java
public static final String EVENT_EXCHANGE = "big.market.events.v1";
public static final String DELIVERY_QUEUE = "big.market.award.delivery.v1";
public static final String DELIVERY_ROUTE = "award.delivery.requested.v1";
public static final String DEAD_EXCHANGE = "big.market.dead.v1";
public static final String DEAD_QUEUE = "big.market.award.delivery.dead.v1";
public static final String DEAD_ROUTE = "award.delivery.dead.v1";
```

业务队列参数设置 `x-dead-letter-exchange` 和 `x-dead-letter-routing-key`。`RabbitTemplate` 启用 correlated confirm 和 mandatory return；发布方法必须等待 Confirm，NACK、Return 或超时统一抛出 `MessagePublishException`。

- [ ] **Step 4: 增加连接和 Confirm 配置**

```yaml
spring:
  rabbitmq:
    host: ${BIG_MARKET_RABBIT_HOST:localhost}
    port: ${BIG_MARKET_RABBIT_PORT:5672}
    username: ${BIG_MARKET_RABBIT_USERNAME:guest}
    password: ${BIG_MARKET_RABBIT_PASSWORD:guest}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

- [ ] **Step 5: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 6: Commit**

```powershell
$paths = @(
  'Big-Market-app/pom.xml',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/model/DomainEventEnvelope.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/model/AwardDeliveryRequestedPayload.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/IMessagePublisher.java',
  'Big-Market-app/src/main/java/shirlin/ai/config/RabbitMQConfig.java',
  'Big-Market-app/src/main/java/shirlin/ai/messaging/RabbitMessagePublisher.java',
  'Big-Market-app/src/main/resources/application.yml',
  'Big-Market-app/src/test/java/shirlin/ai/test/Messaging/RabbitMessagePublisherTest.java'
)
git add -- $paths
git commit --only -m "feat: publish award events with RabbitMQ confirms" -- $paths
```

### Task 5：实现 Outbox 发布编排和定时任务

**Files:**

- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxPublishService.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/job/OutboxPublishJob.java`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Messaging/OutboxPublishServiceTest.java`

**Interfaces:**

```java
public int publishBatch(String workerId, int batchSize, Duration lease);
```

- [ ] **Step 1: 写发布状态测试**

覆盖信封字段映射、成功 Confirm 后按 `eventId + lockToken` 标记、发送失败记录摘要、旧令牌更新 0 行不覆盖新租约，以及坏 JSON 作为发布失败而不终止整批。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=OutboxPublishServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，发布编排尚不存在。

- [ ] **Step 3: 实现逐条发布与隔离失败**

`occurredAt` 使用 Outbox `createTime`。每条事件单独捕获异常并调用 `recordPublishFailure`，错误摘要截断到 500 字；一条坏消息不能阻断同批其他事件。

- [ ] **Step 4: 增加可配置调度任务**

```java
@Scheduled(fixedDelayString = "${big-market.delivery.publisher-delay-ms:500}")
public void run() {
    publisher.publishBatch(workerId, batchSize, Duration.ofSeconds(leaseSeconds));
}
```

`workerId` 使用应用名、主机名和进程标识组合，并截断为 64 字符。

- [ ] **Step 5: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 6: Commit**

```powershell
git add -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxPublishService.java Big-Market-app/src/main/java/shirlin/ai/job/OutboxPublishJob.java Big-Market-app/src/test/java/shirlin/ai/test/Messaging/OutboxPublishServiceTest.java
git commit --only -m "feat: dispatch pending outbox events" -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/OutboxPublishService.java Big-Market-app/src/main/java/shirlin/ai/job/OutboxPublishJob.java Big-Market-app/src/test/java/shirlin/ai/test/Messaging/OutboxPublishServiceTest.java
```

---

## Phase 3：幂等消费与积分发放

### Task 6：拆分奖品处理器并强化积分流水幂等

**Files:**

- Create: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/model/valobj/FulfillmentResult.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardFulfillmentHandler.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/PointsAwardFulfillmentHandler.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/ManualAwardFulfillmentHandler.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardFulfillmentService.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/AwardDeliveryService.java`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/AwardDeliveryServiceTest.java`
- Create: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/PointsAwardFulfillmentHandlerTest.java`

**Interfaces:**

```java
public interface AwardFulfillmentHandler {
    boolean supports(String awardKey);
    FulfillmentResult fulfill(AwardDeliveryTask task);
}

public enum FulfillmentResult { SUCCESS, MANUAL }

public class AwardFulfillmentService {
    public FulfillmentResult fulfill(String userId, String orderId);
}
```

- [ ] **Step 1: 写积分处理器测试**

覆盖固定积分、随机积分、非正积分、流水不存在、流水完全一致、流水用户不一致和流水积分不一致。只有流水不存在时才调用 `addPoints`。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=PointsAwardFulfillmentHandlerTest,AwardDeliveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，处理器和显式流水核对尚不存在。

- [ ] **Step 3: 实现积分处理器**

```java
UserPointsLedger ledger = pointsDao.selectLedgerForUpdate(task.getOrderId());
if (ledger == null) {
    if (pointsDao.insertLedger(task.getOrderId(), task.getUserId(), amount) != 1) {
        throw new RetryableDeliveryException("points ledger insert raced");
    }
    if (pointsDao.addPoints(task.getUserId(), amount) != 1) {
        throw new RetryableDeliveryException("points account update failed");
    }
} else if (!ledger.matches(task.getUserId(), amount)) {
    throw new NonRetryableDeliveryException("points ledger conflicts with delivery task");
}
return FulfillmentResult.SUCCESS;
```

`ManualAwardFulfillmentHandler` 只支持明确列出的券、实物等奖品键。未知奖品键不能被通配处理器吞掉，必须抛出不可重试异常。

- [ ] **Step 4: 重构发放编排**

`AwardFulfillmentService` 是本地任务和 MQ 消费者共享的唯一发放核心：锁定任务、根据 `supports` 选择唯一处理器，并统一更新任务和 `user_award_record`。状态为 SUCCESS 时直接返回；MANUAL 表示已正确分流并提交。`AwardDeliveryService.deliver` 保留本地入口和事务边界，但只委托该核心，避免 local 与 mq 两套状态机发生偏差。

- [ ] **Step 5: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS，重复发放只增加一次积分。

- [ ] **Step 6: Commit**

```powershell
$paths = @(
  'Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/model/valobj/FulfillmentResult.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardFulfillmentHandler.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/PointsAwardFulfillmentHandler.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/ManualAwardFulfillmentHandler.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardFulfillmentService.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/AwardDeliveryService.java',
  'Big-Market-app/src/test/java/shirlin/ai/test/Domain/AwardDeliveryServiceTest.java',
  'Big-Market-app/src/test/java/shirlin/ai/test/Domain/PointsAwardFulfillmentHandlerTest.java'
)
git add -- $paths
git commit --only -m "refactor: make award fulfillment idempotent" -- $paths
```

### Task 7：实现 Inbox 与发放应用事务

**Files:**

- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardDeliveryApplicationService.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/DeliveryResult.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/exception/RetryableDeliveryException.java`
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/exception/NonRetryableDeliveryException.java`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryApplicationServiceTest.java`

**Interfaces:**

```java
@Transactional(rollbackFor = Exception.class)
public DeliveryResult deliver(DomainEventEnvelope<AwardDeliveryRequestedPayload> event);
```

- [ ] **Step 1: 写 Inbox 与事务编排测试**

覆盖新消息、相同摘要的已成功 Inbox、不同摘要冲突、任务不存在、用户不一致、任务已成功、处理器成功和人工分流。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=AwardDeliveryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，应用服务尚不存在。

- [ ] **Step 3: 实现规范化摘要与 Inbox 判定**

摘要使用序列化后的规范消息内容计算 SHA-256。先执行 `insertOrTouch`，再 `selectForUpdate`；SUCCESS 且键与摘要一致返回 `DUPLICATE`，不一致抛 `NonRetryableDeliveryException`。

- [ ] **Step 4: 实现任务状态机**

在同一事务内调用共享的 `AwardFulfillmentService`。该核心锁定 `userId + orderId` 任务，将 PENDING 条件更新为 PROCESSING 并增加尝试次数，调用处理器后更新为 SUCCESS 或 MANUAL；MQ 应用服务随后 `markSuccess` Inbox。事务异常时 Inbox、任务状态和积分一起回滚。

- [ ] **Step 5: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 6: Commit**

```powershell
$paths = @(
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/AwardDeliveryApplicationService.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/DeliveryResult.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/exception/RetryableDeliveryException.java',
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/messaging/exception/NonRetryableDeliveryException.java',
  'Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryApplicationServiceTest.java'
)
git add -- $paths
git commit --only -m "feat: consume award events with transactional inbox" -- $paths
```

### Task 8：实现消费者指数退避、失败记录和死信处理

**Files:**

- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/DeliveryFailureRecorder.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryConsumer.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryDeadLetterConsumer.java`
- Modify: `Big-Market-app/src/main/java/shirlin/ai/config/RabbitMQConfig.java`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryConsumerTest.java`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryDeadLetterConsumerTest.java`

**Interfaces:**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordFailure(String orderId, String reason);
```

- [ ] **Step 1: 写消费者分类和 ACK 语义测试**

覆盖成功、重复成功、可重试异常、不可重试异常、第五次耗尽、无 `orderId` 的坏消息，以及错误摘要截断。永久错误必须直接拒绝，不能经过 1、2、4、8 秒等待。

- [ ] **Step 2: 写死信状态保护测试**

死信只允许将 PENDING/PROCESSING 更新为 FAILED 并写 `MQ_DEAD_LETTER` 审计；SUCCESS/MANUAL 不被覆盖，重复死信不新增重复业务结果。

- [ ] **Step 3: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=AwardDeliveryConsumerTest,AwardDeliveryDeadLetterConsumerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，监听器、失败记录器和重试配置尚不存在。

- [ ] **Step 4: 配置随机化指数退避**

```java
ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
backOff.setInitialInterval(1_000L);
backOff.setMultiplier(2.0);
backOff.setMaxInterval(30_000L);

Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
retryable.put(NonRetryableDeliveryException.class, false);
retryable.put(Exception.class, true);
RetryTemplate template = new RetryTemplate();
template.setRetryPolicy(new SimpleRetryPolicy(5, retryable, true, true));
template.setBackOffPolicy(backOff);
```

将该模板装配到无状态 Rabbit Retry Interceptor，Recoverer 使用 `RejectAndDontRequeueRecoverer`。监听容器使用 AUTO ACK：业务方法正常返回后 ACK，异常时由拦截器重试或拒绝进入 DLQ。

- [ ] **Step 5: 实现失败记录边界**

监听器捕获业务异常后调用 `DeliveryFailureRecorder`，该服务以 `REQUIRES_NEW` 事务只更新仍为 PENDING 的任务，然后原样抛出异常。永久错误用 `AmqpRejectAndDontRequeueException` 保留原因链并直接死信。

- [ ] **Step 6: 实现死信监听器**

从死信消息和 `x-death` 头提取事件与失败原因，锁定任务并执行条件更新与审计。无法解析 `orderId` 时记录结构化错误并 ACK，避免有毒消息阻塞死信队列；原始消息标识保留在日志中供人工处理。

- [ ] **Step 7: 运行测试并确认通过**

Run: 使用 Step 3 命令。

Expected: PASS。

- [ ] **Step 8: Commit**

```powershell
$paths = @(
  'Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/delivery/DeliveryFailureRecorder.java',
  'Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryConsumer.java',
  'Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryDeadLetterConsumer.java',
  'Big-Market-app/src/main/java/shirlin/ai/config/RabbitMQConfig.java',
  'Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryConsumerTest.java',
  'Big-Market-app/src/test/java/shirlin/ai/test/Messaging/AwardDeliveryDeadLetterConsumerTest.java'
)
git add -- $paths
git commit --only -m "feat: retry and dead-letter award consumption" -- $paths
```

---

## Phase 4：运营闭环与灰度切换

### Task 9：人工重试生成带版本的新 Outbox

**Files:**

- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/AwardDeliveryService.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IAwardDeliveryTaskDao.java`
- Modify: `Big-Market-app/src/main/resources/mybatis/mapper/AwardDeliveryTaskMapper.xml`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/AwardDeliveryOperationsTest.java`

- [ ] **Step 1: 修改运营重试测试**

断言 `retry` 在同一事务中锁定 MANUAL/FAILED 任务、将 `dispatchVersion` 加一、重置任务、写审计，并调用：

```java
outbox.append(
        "AWARD_DELIVERY_REQUESTED",
        "award-delivery:" + orderId + ":v" + nextVersion,
        orderId,
        task.getUserId(),
        Map.of("orderId", orderId, "userId", task.getUserId()));
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=AwardDeliveryOperationsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，现有 `retry` 只重置任务和写审计。

- [ ] **Step 3: 实现带版本重置和 Outbox 原子写入**

Mapper 用 `dispatch_version = dispatch_version + 1` 条件更新，并返回后重新读取或由锁定对象计算 `nextVersion`。`TransactionalOutboxService.append` 的 MANDATORY 事务确保任务、审计和新事件一起提交或回滚。

- [ ] **Step 4: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 5: Commit**

```powershell
git add -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/AwardDeliveryService.java Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IAwardDeliveryTaskDao.java Big-Market-app/src/main/resources/mybatis/mapper/AwardDeliveryTaskMapper.xml Big-Market-app/src/test/java/shirlin/ai/test/Domain/AwardDeliveryOperationsTest.java
git commit --only -m "feat: redispatch failed awards with versioned events" -- Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/AwardDeliveryService.java Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/dao/IAwardDeliveryTaskDao.java Big-Market-app/src/main/resources/mybatis/mapper/AwardDeliveryTaskMapper.xml Big-Market-app/src/test/java/shirlin/ai/test/Domain/AwardDeliveryOperationsTest.java
```

### Task 10：增加 local、mq-prepare、mq 条件装配

**Files:**

- Create: `Big-Market-app/src/main/java/shirlin/ai/config/DeliveryMode.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/config/DeliveryModeProperties.java`
- Modify: `Big-Market-app/src/main/java/shirlin/ai/job/AwardDeliveryJob.java`
- Modify: `Big-Market-app/src/main/java/shirlin/ai/job/OutboxPublishJob.java`
- Modify: `Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryConsumer.java`
- Modify: `Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryDeadLetterConsumer.java`
- Modify: `Big-Market-app/src/main/resources/application.yml`
- Modify: `Big-Market-app/src/main/resources/application-dev.yml`
- Test: `Big-Market-app/src/test/java/shirlin/ai/test/Config/DeliveryModeConfigurationTest.java`

- [ ] **Step 1: 写三种模式的上下文测试**

| 模式 | 本地任务 | Outbox 发布 | 业务消费者 | 死信消费者 |
|---|---:|---:|---:|---:|
| `local` | 开 | 关 | 关 | 关 |
| `mq-prepare` | 开 | 开 | 关 | 关 |
| `mq` | 关 | 开 | 开 | 开 |

未知值必须使应用启动失败，不能静默回退。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -Dtest=DeliveryModeConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，当前本地任务无条件装配。

- [ ] **Step 3: 实现枚举配置和条件装配**

`DeliveryModeProperties` 使用 `@ConfigurationProperties("big-market.delivery")` 和 `@Validated`。用明确的条件注解或配置类创建 Job/Listener Bean，避免仅在方法内部提前返回造成无效监听容器仍连接 RabbitMQ。

- [ ] **Step 4: 增加默认 local 配置**

```yaml
big-market:
  delivery:
    mode: ${BIG_MARKET_DELIVERY_MODE:local}
    publisher-delay-ms: 500
    publisher-batch-size: 100
    outbox-lease-seconds: 30
```

- [ ] **Step 5: 运行测试并确认通过**

Run: 使用 Step 2 命令。

Expected: PASS。

- [ ] **Step 6: Commit**

```powershell
$paths = @(
  'Big-Market-app/src/main/java/shirlin/ai/config/DeliveryMode.java',
  'Big-Market-app/src/main/java/shirlin/ai/config/DeliveryModeProperties.java',
  'Big-Market-app/src/main/java/shirlin/ai/job/AwardDeliveryJob.java',
  'Big-Market-app/src/main/java/shirlin/ai/job/OutboxPublishJob.java',
  'Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryConsumer.java',
  'Big-Market-app/src/main/java/shirlin/ai/messaging/AwardDeliveryDeadLetterConsumer.java',
  'Big-Market-app/src/main/resources/application.yml',
  'Big-Market-app/src/main/resources/application-dev.yml',
  'Big-Market-app/src/test/java/shirlin/ai/test/Config/DeliveryModeConfigurationTest.java'
)
git add -- $paths
git commit --only -m "feat: switch award delivery modes safely" -- $paths
```

---

## Phase 5：联调、恢复和验收

### Task 11：补齐真实 MySQL 与 RabbitMQ 联调场景

**Files:**

- Create: `Big-Market-app/src/test/java/shirlin/ai/test/Integration/RabbitAwardDeliveryIntegrationTest.java`
- Create: `docs/dev-ops/rabbitmq/award-delivery-runbook.md`
- Modify: `docs/dev-ops/mysql/2026-09-23-validation-report.md`

- [ ] **Step 1: 编写环境开关的联调测试**

仅在 `BIG_MARKET_REAL_INTEGRATION=true` 时运行，连接真实 MySQL 和 RabbitMQ，不用内存数据库模拟锁或 Confirm。

- [ ] **Step 2: 覆盖五个故障窗口**

1. 重复发布相同 `eventId`，Inbox 只成功一次。
2. 发布 Confirm 成功后故意跳过 Outbox 标记，租约过期重发仍只加一次积分。
3. 消费事务提交后主动抛出 ACK 前异常，重放后账户不变。
4. 同一 `eventId` 修改消息摘要，消息进入死信且账户不变。
5. 两个不同人工重试事件指向同一订单，积分流水仍只有一条。

- [ ] **Step 3: 编写运行手册**

手册包含拓扑创建/检查、模式切换、Outbox/Inbox/任务积压 SQL、死信检查、人工重试、回退到 local 的顺序，以及必须先停止 MQ 消费者再启用本地任务的约束。

- [ ] **Step 4: 执行单库联调**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:BIG_MARKET_REAL_INTEGRATION='true'
mvn -pl Big-Market-app -am -Dtest=RabbitAwardDeliveryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；验证报告记录 MySQL/RabbitMQ 版本、执行时间、用例结果和任何环境限制。

- [ ] **Step 5: Commit**

```powershell
git add -- Big-Market-app/src/test/java/shirlin/ai/test/Integration/RabbitAwardDeliveryIntegrationTest.java docs/dev-ops/rabbitmq/award-delivery-runbook.md docs/dev-ops/mysql/2026-09-23-validation-report.md
git commit --only -m "test: verify reliable RabbitMQ award delivery" -- Big-Market-app/src/test/java/shirlin/ai/test/Integration/RabbitAwardDeliveryIntegrationTest.java docs/dev-ops/rabbitmq/award-delivery-runbook.md docs/dev-ops/mysql/2026-09-23-validation-report.md
```

### Task 12：全量回归与最终审查

**Files:**

- Modify only files required by failures found in this task.

- [ ] **Step 1: 运行消息链路定向测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am '-Dtest=*AwardDelivery*,*Outbox*,*Rabbit*,*DeliveryMode*,*PointsAward*' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 2: 运行全量测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am test
```

Expected: 全部非环境型测试 PASS；环境型测试只有在开关未启用时 SKIPPED。

- [ ] **Step 3: 编译生产代码并校验 XML/空白**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am -DskipTests package
git diff --check
```

Expected: Maven `BUILD SUCCESS`，`git diff --check` 无输出。

- [ ] **Step 4: 核对关键不变量**

```powershell
rg -n "INSERT IGNORE" Big-Market-app/src/main/resources/mybatis/mapper/UserPointsMapper.xml
rg -n "AWARD_DELIVERY_REQUESTED" Big-Market-app Big-Market-infrastructure
rg -n "ExponentialRandomBackOffPolicy|SimpleRetryPolicy\(5" Big-Market-app
rg -n "lock_token|FOR UPDATE SKIP LOCKED" Big-Market-app/src/main/resources/mybatis/mapper/OutboxEventMapper.xml
```

Expected: 积分 Mapper 不含 `INSERT IGNORE`；其余三项均命中预期实现。

- [ ] **Step 5: 审查工作区和提交范围**

```powershell
git status --short
git log --oneline --decorate -15
```

只报告本计划产生的提交和已验证结果。保留计划开始前已有的其他工作区修改，不重置、不覆盖、不混入提交。
