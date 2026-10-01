# 积分奖品 RabbitMQ 发放运行手册

## 1. 适用范围

本手册用于 `AWARD_DELIVERY_REQUESTED` 的 Outbox 发布、RabbitMQ 消费、死信处理和人工重试。MySQL 是 Outbox、Inbox、发放任务、积分账户和积分流水的权威数据源；RabbitMQ 提供至少一次投递。

## 2. 上线前检查

按顺序执行迁移，并确认每个脚本末尾的对账查询没有异常行：

1. `2026-09-22-draw-delivery.sql`
2. `2026-09-23-business-entry.sql`
3. `2026-09-24-inventory-bucket-outbox.sql`
4. `2026-09-28-outbox-rabbitmq-award-delivery.sql`

重点确认：

```sql
SHOW COLUMNS FROM award_delivery_task LIKE 'dispatch_version';
SHOW COLUMNS FROM outbox_event LIKE 'lock_token';
SHOW TABLES LIKE 'inbox_event';
SHOW INDEX FROM outbox_event WHERE Key_name = 'uk_outbox_event_key';
```

RabbitMQ 需要创建以下持久化拓扑：

| 类型 | 名称 | 路由键 |
|---|---|---|
| Topic Exchange | `big.market.events.v1` | `award.delivery.requested.v1` |
| Queue | `big.market.award.delivery.v1` | 绑定业务 Exchange |
| Topic Exchange | `big.market.dead.v1` | `award.delivery.dead.v1` |
| Queue | `big.market.award.delivery.dead.v1` | 绑定死信 Exchange |

业务队列必须设置：

```text
x-dead-letter-exchange=big.market.dead.v1
x-dead-letter-routing-key=award.delivery.dead.v1
```

应用启动时会幂等声明拓扑。可在 RabbitMQ Management 的 Exchanges、Queues 页面检查 durable、bindings 和 arguments。

## 3. 环境变量

```powershell
$env:BIG_MARKET_JDBC_URL='jdbc:mysql://127.0.0.1:13306/Big-Market?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:BIG_MARKET_DB_USER='root'
$env:BIG_MARKET_DB_PASSWORD='replace-me'
$env:BIG_MARKET_RABBIT_HOST='127.0.0.1'
$env:BIG_MARKET_RABBIT_PORT='5672'
$env:BIG_MARKET_RABBIT_USERNAME='guest'
$env:BIG_MARKET_RABBIT_PASSWORD='guest'
```

生产环境需要使用独立 RabbitMQ 账号和 vhost，并授予上述 Exchange、Queue 的 configure/write/read 权限。

## 4. 运行模式

通过 `BIG_MARKET_DELIVERY_MODE` 选择唯一入口：

| 值 | 本地发放任务 | Outbox 发布 | 业务消费者 | 死信消费者 |
|---|---:|---:|---:|---:|
| `local` | 开 | 关 | 关 | 关 |
| `mq-prepare` | 开 | 开 | 关 | 关 |
| `mq` | 关 | 开 | 开 | 开 |

未知值会使应用启动失败。默认值为 `local`。

### 从 local 切换到 mq

1. 完成 MySQL 迁移和 RabbitMQ 拓扑检查。
2. 以 `local` 部署新版本，确认本地发放正常。
3. 切到 `mq-prepare`，确认 Outbox 能发布、RabbitMQ 业务队列开始积压且本地任务继续完成发放。
4. 检查 Inbox、积分流水和任务状态没有不一致。
5. 切到 `mq`，确认本地 `AwardDeliveryJob` 已停止，随后消费者开始清理积压。

### 从 mq 回退到 local

1. 先停止所有 `mq` 实例或将它们切到不启动消费者的配置。
2. 确认 RabbitMQ 业务消费者数为 0。
3. 再启动 `local` 实例。
4. 检查 PENDING 任务被本地任务继续处理。

必须先停 MQ 消费者再启用本地任务，避免回退期间两条入口同时运行。

## 5. 日常监控 SQL

### Outbox 积压与最老事件

```sql
SELECT status, COUNT(*) AS event_count, MIN(create_time) AS oldest_event
FROM outbox_event
WHERE event_type = 'AWARD_DELIVERY_REQUESTED'
GROUP BY status;

SELECT event_id, event_key, attempts, next_attempt_at, locked_by, locked_until, last_error
FROM outbox_event
WHERE event_type = 'AWARD_DELIVERY_REQUESTED' AND status IN (0, 2)
ORDER BY create_time
LIMIT 100;
```

### Inbox 重复投递

```sql
SELECT event_id, event_key, aggregate_id, status, duplicate_count,
       first_received_time, last_received_time
FROM inbox_event
WHERE consumer_name = 'award-delivery-consumer-v1'
ORDER BY last_received_time DESC
LIMIT 100;
```

### 发放任务积压

```sql
SELECT status, COUNT(*) AS task_count, MIN(update_time) AS oldest_update
FROM award_delivery_task
GROUP BY status;

SELECT order_id, user_id, status, attempts, dispatch_version,
       next_retry_at, last_error
FROM award_delivery_task
WHERE status IN (0, 3, 4)
ORDER BY update_time
LIMIT 100;
```

### 一致性检查

```sql
SELECT task.order_id, task.user_id, task.status, ledger.points
FROM award_delivery_task task
LEFT JOIN user_points_ledger ledger ON ledger.order_id = task.order_id
WHERE task.award_key IN ('user_points', 'random_points')
  AND ((task.status = 2 AND ledger.order_id IS NULL)
       OR (task.status <> 2 AND ledger.order_id IS NOT NULL));

SELECT ledger.order_id, COUNT(*)
FROM user_points_ledger ledger
GROUP BY ledger.order_id
HAVING COUNT(*) > 1;
```

## 6. 死信处理

在 RabbitMQ Management 检查 `big.market.award.delivery.dead.v1` 的 Ready、Unacked 和消息头 `x-death`。死信消费者会将仍处于 PENDING/PROCESSING 的任务条件更新为 FAILED，并写入 `MQ_DEAD_LETTER` 审计；SUCCESS 或 MANUAL 不会被死信覆盖。

排查顺序：

1. 根据消息的 `orderId` 查询 `award_delivery_task`、`inbox_event` 和 `user_points_ledger`。
2. 根据 `last_error` 判断是协议错误、数据冲突还是依赖故障。
3. 修复数据或依赖后，通过运营 API 生成新版本事件。
4. 不直接修改积分账户，不把旧死信原样重新发布。

## 7. 人工重试与人工结案

仅 `operations` 角色可调用：

```http
POST /api/v1/admin/award-deliveries/{orderId}/retry
Authorization: Bearer <operations-jwt>
Content-Type: application/json

{"note":"依赖恢复，重新发放"}
```

重试会在同一 MySQL 事务中锁定任务、递增 `dispatch_version`、重置为 PENDING、写审计，并生成 `award-delivery:{orderId}:v{dispatchVersion}` 新 Outbox。

无法自动履约时人工结案：

```http
POST /api/v1/admin/award-deliveries/{orderId}/complete
Authorization: Bearer <operations-jwt>
Content-Type: application/json

{"success":true,"note":"线下核验完成"}
```

## 8. 真实联调

先准备独立测试库并执行迁移，再运行：

```powershell
$env:BIG_MARKET_REAL_INTEGRATION='true'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -pl Big-Market-app -am '-Dtest=RabbitAwardDeliveryIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

测试只清理 `itmq-` 前缀的数据，并覆盖重复发布、Confirm 后未标记、提交后 ACK 丢失、协议冲突死信和不同重试事件指向同一订单五个窗口。不要指向生产库执行。
