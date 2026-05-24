# Repository Split: Strategy / Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `StrategyRepository` 拆分为 `StrategyRepository`（策略域）和 `ActivityRepository`（活动域），消除跨域职责混杂。

**Architecture:** 在 `domain/activity/adapter/repository/` 新增 `IActivityRepository` 接口，承接活动 SKU 库存、参与订单、用户 activity_account 三层额度等活动域操作；`IStrategyRepository` 仅保留策略配置、概率缓存、奖品库存及用户状态查询。基础设施层新增 `ActivityRepository` 实现类，`StrategyRepository` 同步清理掉所有活动相关 DAO 注入和方法。

**Tech Stack:** Java 17 · Spring Boot 3 · MyBatis · Redisson (`IRedisService`) · Lombok · fastjson2

---

## 文件变更总览

| 操作 | 文件 |
|---|---|
| 新建 | `Big-Market-domain/src/main/java/shirlin/ai/domain/activity/adapter/repository/IActivityRepository.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/adapter/repository/IStrategyRepository.java` |
| 新建 | `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/ActivityRepository.java` |
| 修改 | `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/StrategyRepository.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Impl/ActivitySkuStockHandler.java` |

---

## Task 1：新建 `IActivityRepository` 接口

**Files:**
- Create: `Big-Market-domain/src/main/java/shirlin/ai/domain/activity/adapter/repository/IActivityRepository.java`

- [ ] **Step 1：创建 domain 接口文件**

```java
package shirlin.ai.domain.activity.adapter.repository;

public interface IActivityRepository {

    // ---- 活动 SKU 库存（责任链 Node2） ----

    /**
     * 扣减活动 SKU 库存
     * 流程：decr → <0 则补偿返回 false；≥0 则 setnx 锁定序号 + 写延迟队列
     */
    boolean deductActivitySkuStock(Long strategyId);

    /**
     * 活动上线时将 SKU 总库存写入 Redis
     */
    void cacheActivitySkuStock(Long strategyId, Long totalStock);

    // ---- 参与订单（Phase 2 事务内） ----

    /**
     * 创建用户参与订单（state=0，awardId=0 占位）
     * @return 生成的订单 ID
     */
    Long createUserRaffleOrder(String userId, Long strategyId);

    /**
     * 抽奖完成后回填订单 awardId 并将状态改为 1（completed）
     */
    void updateUserRaffleOrder(Long orderId, Integer awardId, Integer awardType);

    // ---- 用户活动额度扣减（activity_account，Phase 2 事务内） ----

    /**
     * 扣减总剩余次数（activity_account.totalCountSurplus--，乐观更新）
     * @return true 成功，false 额度不足
     */
    boolean deductUserTotalQuota(String userId, Long strategyId);

    /**
     * 扣减月剩余次数
     * Redis 快速拦截（首次从 activity_account.monthCount 初始化，TTL 到月底）
     * + DB deductMonthSurplus 兜底
     * @return true 成功，false 月额度已耗尽
     */
    boolean deductUserMonthlyQuota(String userId, Long strategyId);

    /**
     * 扣减日剩余次数
     * Redis 快速拦截（首次从 activity_account.dayCount 初始化，TTL 到当天 23:59:59）
     * + DB deductDaySurplus 兜底
     * @return true 成功，false 日额度已耗尽
     */
    boolean deductUserDailyQuota(String userId, Long strategyId);
}
```

- [ ] **Step 2：确认文件存在，包路径正确**

```
Big-Market-domain/src/main/java/shirlin/ai/domain/activity/adapter/repository/IActivityRepository.java
```

---

## Task 2：裁剪 `IStrategyRepository`，移除活动相关方法

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/adapter/repository/IStrategyRepository.java`

- [ ] **Step 1：删除以下 7 个方法声明**

删除的方法（全部迁移到 `IActivityRepository`）：
- `boolean deductActivitySkuStock(Long strategyId)`
- `void cacheActivitySkuStock(Long strategyId, Long totalStock)`
- `Long createUserRaffleOrder(String userId, Long strategyId)`
- `void updateUserRaffleOrder(Long orderId, Integer awardId, Integer awardType)`
- `boolean deductUserTotalQuota(String userId, Long strategyId)`
- `boolean deductUserMonthlyQuota(String userId, Long strategyId, int monthlyLimit)`
- `boolean deductUserDailyQuota(String userId, Long strategyId, int dailyLimit)`

最终 `IStrategyRepository.java` 保留的完整内容：

```java
package shirlin.ai.domain.strategy.adapter.repository;

import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;

import java.util.List;
import java.util.Set;

public interface IStrategyRepository {

    // ---- Strategy / Award 基础查询 ----

    List<StrategyAwardEntity> queryStrategyAwardListById(Long strategyId);

    StrategyAwardEntity queryStrategyAward(Long strategyId, Integer awardId);

    StrategyEntity queryStrategyById(Long strategyId);

    // ---- 规则配置查询 ----

    StrategyRuleEntity queryStrategyRuleByModel(Long strategyId, String ruleModel);

    // ---- Redis 缓存：概率区间 / 精度 / 兜底奖品 ----

    void storeStrategyAwardRangeTable(Long strategyId, List<AwardRateRange> rangeTable);

    void storeStrategyPrecision(Long strategyId, int precision);

    void storeStrategyMaxAward(Long strategyId, StrategyAwardEntity maxAward);

    int getStrategyPrecision(Long strategyId);

    List<AwardRateRange> getStrategyRangeTable(Long strategyId);

    StrategyAwardEntity getStrategyMaxAward(Long strategyId);

    List<AwardRateRange> getOrBuildSubRangeTable(Long strategyId, Set<Integer> excludeAwardIds, String cacheKey);

    // ---- 奖品库存操作（规则树 Stock 节点） ----

    boolean deductStock(Long strategyId, Integer awardId);

    void cacheStrategyAwardStock(Long strategyId, Integer awardId, Integer awardSurplus);

    Integer queryMaxAwardId(Long strategyId);

    // ---- 用户状态查询 ----

    int queryUserDrawCount(String userId, Long strategyId);

    int queryUserWeightValue(String userId, Long strategyId);

    int queryUserLuckValue(String userId, Long strategyId);

    void incrementLuckValue(String userId, Long strategyId);

    void resetUserLuckValue(String userId, Long strategyId);
}
```

---

## Task 3：新建 `ActivityRepository` 实现类

**Files:**
- Create: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/ActivityRepository.java`

- [ ] **Step 1：创建 ActivityRepository.java**

```java
package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IActivityDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;
import shirlin.ai.infrastructure.dao.po.UserAwardRecord;
import shirlin.ai.infrastructure.redis.IRedisService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Repository
public class ActivityRepository implements IActivityRepository {

    // ---- Redis key 前缀（活动域） ----
    private static final String SKU_STOCK_KEY = "big_market:activity:sku:stock:";
    private static final String SKU_STOCK_LOCK_KEY = "big_market:activity:sku:stock:lock:";
    private static final String SKU_STOCK_QUEUE_KEY = "big_market:activity:sku:stock:queue";
    private static final String MONTHLY_QUOTA_KEY = "big_market:user:quota:monthly:";
    private static final String DAILY_QUOTA_KEY = "big_market:user:quota:daily:";

    @Resource
    private IActivityDao activityDao;

    @Resource
    private IActivityAccountDao activityAccountDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    @Resource
    private IRedisService redisService;

    // ---- 私有辅助 ----

    private Long queryActivityId(Long strategyId) {
        return activityDao.selectByStrategyId(strategyId).getActivityId();
    }

    // ---- 活动 SKU 库存 ----

    @Override
    public boolean deductActivitySkuStock(Long strategyId) {
        String stockKey = SKU_STOCK_KEY + strategyId;

        if (!redisService.isExists(stockKey)) {
            return true;
        }

        long remaining = redisService.decr(stockKey);
        if (remaining < 0) {
            redisService.setValue(stockKey, 0L);
            return false;
        }

        String lockKey = SKU_STOCK_LOCK_KEY + strategyId + ":" + remaining;
        boolean locked = redisService.setNx(lockKey);
        if (!locked) {
            redisService.incrBy(stockKey, 1L);
            return false;
        }

        org.redisson.api.RBlockingQueue<Long> blockingQueue =
                redisService.getBlockingQueue(SKU_STOCK_QUEUE_KEY);
        org.redisson.api.RDelayedQueue<Long> delayedQueue =
                redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyId, 3, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public void cacheActivitySkuStock(Long strategyId, Long totalStock) {
        redisService.setAtomicLong(SKU_STOCK_KEY + strategyId, totalStock);
    }

    // ---- 参与订单 ----

    @Override
    public Long createUserRaffleOrder(String userId, Long strategyId) {
        UserAwardRecord po = new UserAwardRecord();
        po.setUserId(userId);
        po.setStrategyId(strategyId);
        po.setAwardId(0);
        po.setAwardType(0);
        po.setAwardContent("");
        po.setAwardState(0);
        po.setDrawTime(new Date());
        userAwardRecordDao.insertOrder(po);
        return po.getId();
    }

    @Override
    public void updateUserRaffleOrder(Long orderId, Integer awardId, Integer awardType) {
        userAwardRecordDao.updateOrderResult(orderId, awardId, awardType);
    }

    // ---- 用户活动额度扣减 ----

    @Override
    public boolean deductUserTotalQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);
        int affected = activityAccountDao.deductTotalSurplus(userId, activityId);
        return affected > 0;
    }

    @Override
    public boolean deductUserMonthlyQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);

        String yearMonth = YearMonth.now().toString();
        String key = MONTHLY_QUOTA_KEY + userId + ":" + activityId + ":" + yearMonth;

        if (!redisService.isExists(key)) {
            ActivityAccount account = activityAccountDao.selectByUserIdAndActivityId(userId, activityId);
            int monthLimit = (account != null && account.getMonthCount() != null)
                    ? account.getMonthCount() : 30;
            redisService.setAtomicLong(key, monthLimit);
            long ttl = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(),
                    YearMonth.now().atEndOfMonth().atTime(23, 59, 59));
            redisService.setExpire(key, ttl, TimeUnit.SECONDS);
        }

        long remaining = redisService.decr(key);
        if (remaining < 0) {
            redisService.incrBy(key, 1L);
            return false;
        }

        int affected = activityAccountDao.deductMonthSurplus(userId, activityId);
        if (affected == 0) {
            redisService.incrBy(key, 1L);
            return false;
        }
        return true;
    }

    @Override
    public boolean deductUserDailyQuota(String userId, Long strategyId) {
        Long activityId = queryActivityId(strategyId);

        String today = LocalDate.now().toString();
        String key = DAILY_QUOTA_KEY + userId + ":" + activityId + ":" + today;

        if (!redisService.isExists(key)) {
            ActivityAccount account = activityAccountDao.selectByUserIdAndActivityId(userId, activityId);
            int dayLimit = (account != null && account.getDayCount() != null)
                    ? account.getDayCount() : 5;
            redisService.setAtomicLong(key, dayLimit);
            LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
            long ttl = ChronoUnit.SECONDS.between(LocalDateTime.now(), endOfDay);
            redisService.setExpire(key, ttl, TimeUnit.SECONDS);
        }

        long remaining = redisService.decr(key);
        if (remaining < 0) {
            redisService.incrBy(key, 1L);
            return false;
        }

        int affected = activityAccountDao.deductDaySurplus(userId, activityId);
        if (affected == 0) {
            redisService.incrBy(key, 1L);
            return false;
        }
        return true;
    }
}
```

---

## Task 4：裁剪 `StrategyRepository`，移除活动相关代码

**Files:**
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/StrategyRepository.java`

- [ ] **Step 1：删除活动域 DAO 注入**

移除以下三个字段：
```java
@Resource
private IActivityAccountDao activityAccountDao;   // 删除

@Resource
private IUserDrawAccountDao userDrawAccountDao;   // 删除（全程未用，清理）
```
`IActivityDao` 也移除（strategy 域不需要）：
```java
// 无需新增 IActivityDao — 原本就没有注入
```

保留的 DAO 注入（完整列表）：
```java
@Resource
private IStrategyDao strategyDao;

@Resource
private IStrategyAwardDao strategyAwardDao;

@Resource
private IStrategyRuleDao strategyRuleDao;

@Resource
private IUserAwardRecordDao userAwardRecordDao;

@Resource
private IUserLuckAccountDao userLuckAccountDao;

@Resource
private IRedisService redisService;
```

- [ ] **Step 2：删除活动域 Redis key 常量**

删除以下常量：
```java
private static final String SKU_STOCK_KEY          = "big_market:activity:sku:stock:";
private static final String SKU_STOCK_LOCK_KEY     = "big_market:activity:sku:stock:lock:";
private static final String SKU_STOCK_QUEUE_KEY    = "big_market:activity:sku:stock:queue";
private static final String USER_MONTHLY_QUOTA_KEY = "big_market:user:quota:monthly:";
private static final String USER_DAILY_QUOTA_KEY   = "big_market:user:quota:daily:";
```

- [ ] **Step 3：删除活动域方法实现**

删除以下 7 个 `@Override` 方法（均已迁移到 `ActivityRepository`）：
- `deductActivitySkuStock`
- `cacheActivitySkuStock`
- `createUserRaffleOrder`
- `updateUserRaffleOrder`
- `deductUserTotalQuota`
- `deductUserMonthlyQuota`
- `deductUserDailyQuota`

- [ ] **Step 4：清理 import**

删除无用 import：
```java
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IUserDrawAccountDao;
import shirlin.ai.infrastructure.dao.po.UserAwardRecord;  // 仍被 queryUserDrawCount 间接用到，保留
```
注意：`IUserAwardRecordDao` 仍被 `queryUserDrawCount` 使用，**不要删除**。

最终 `StrategyRepository` 保留方法列表：
- `queryStrategyAwardListById`
- `queryStrategyAward`
- `queryStrategyById`
- `queryStrategyRuleByModel`
- `storeStrategyAwardRangeTable` / `storeStrategyPrecision` / `storeStrategyMaxAward`
- `getStrategyPrecision` / `getStrategyRangeTable` / `getStrategyMaxAward`
- `queryMaxAwardId`
- `getOrBuildSubRangeTable`
- `deductStock`
- `cacheStrategyAwardStock`
- `queryUserDrawCount`
- `queryUserWeightValue`
- `queryUserLuckValue` / `incrementLuckValue` / `resetUserLuckValue`

---

## Task 5：更新 `RaffleService`

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java`

- [ ] **Step 1：注入 `IActivityRepository` 并清理常量**

将文件整体替换为：

```java
package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.ActivityChainHandlerFactory;
import shirlin.ai.domain.strategy.service.IRaffleService;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

@Slf4j
@Service
public class RaffleService implements IRaffleService {

    @Resource
    private ActivityChainHandlerFactory activityChainHandlerFactory;

    @Resource
    private IStrategyRepository strategyRepository;

    @Resource
    private IActivityRepository activityRepository;

    @Resource
    private IRaffleStrategy raffleStrategy;

    @Override
    public RaffleResultEntity doRaffle(String userId, Long strategyId) {

        // Phase 1：活动校验责任链
        activityChainHandlerFactory.getChainHead().apply(userId, strategyId);

        // Phase 2：事务内操作（创建订单 + 扣减三层额度）
        Long orderId = createOrderAndDeductQuota(userId, strategyId);

        // Phase 3：执行抽奖
        RaffleFactorEntity factor = RaffleFactorEntity.builder()
                .userId(userId)
                .strategyId(strategyId)
                .build();
        RaffleResultEntity result = raffleStrategy.performRaffle(factor);

        // Phase 4：回填订单
        activityRepository.updateUserRaffleOrder(orderId, result.getAwardId(), result.getAwardType());

        log.info("抽奖完成 userId:{} strategyId:{} awardId:{}", userId, strategyId, result.getAwardId());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOrderAndDeductQuota(String userId, Long strategyId) {

        Long orderId = activityRepository.createUserRaffleOrder(userId, strategyId);

        if (!activityRepository.deductUserTotalQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserMonthlyQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserDailyQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        return orderId;
    }
}
```

---

## Task 6：更新 `ActivitySkuStockHandler`

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Impl/ActivitySkuStockHandler.java`

- [ ] **Step 1：改注入 `IActivityRepository`**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.AbstractActivityChainHandler;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

@Slf4j
@Service("activitySkuStockHandler")
public class ActivitySkuStockHandler extends AbstractActivityChainHandler {

    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean apply(String userId, Long strategyId) {

        boolean stockOk = activityRepository.deductActivitySkuStock(strategyId);
        if (!stockOk) {
            log.warn("SKU 库存不足 strategyId:{}", strategyId);
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_EMPTY.getInfo());
        }

        return next(userId, strategyId);
    }
}
```

---

## Task 7：验证编译

- [ ] **Step 1：执行 Maven 编译**

在项目根目录执行：
```bash
mvn compile -pl Big-Market-domain,Big-Market-infrastructure,Big-Market-app --also-make
```

预期输出：`BUILD SUCCESS`，无 `error` 行。

- [ ] **Step 2：若编译失败，按以下顺序排查**

| 错误类型 | 原因 | 修复 |
|---|---|---|
| `cannot find symbol: deductRemainingCount` | `StrategyRepository` 残留旧调用 | 确认 Task 4 Step 3 是否删干净 |
| `cannot find symbol: IActivityRepository` | import 缺失 | 检查 Task 5/6 中 import 行 |
| `does not implement abstract method` | `StrategyRepository` 实现了已删除的接口方法 | 接口已在 Task 2 删除，无需实现 |

- [ ] **Step 3：提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/activity \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/adapter/repository/IStrategyRepository.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Impl/ActivitySkuStockHandler.java \
        Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/ActivityRepository.java \
        Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/StrategyRepository.java
git commit -m "refactor: split StrategyRepository into Strategy and Activity domains"
```
