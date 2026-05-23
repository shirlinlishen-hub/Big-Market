# 抽奖规则过滤重构设计

**日期**: 2026-05-23  
**分支**: table-design  
**方案**: B — `doApply()` 叶子逻辑 + 外部顺序遍历

---

## 背景与问题

当前抽奖规则过滤存在三处结构性问题：

1. **活动校验链** — 链签名为 `(String userId, Long strategyId)`，`ActivityFactorEntity` 中的 `activityId` 和 `skuId` 在链内无法访问。
2. **抽奖前责任链** — 存在两套并行系统：`StrategyPreRuleFilterFactory` 组装了 `BusinessLinkedList`，但 `DefaultRaffleStrategy` 绕过它直接调用 `DefaultLogicFactory.getFilter()`。前者过滤器实现 `ILogicHandler`，后者 Map 类型为 `IStrategyLogicFilterService`，类型不匹配，运行时抛异常。`DynamicContext` 为空，权重过滤结果无法通过 ctx 传递。
3. **规则树** — `AbstractRuleFilterService` 泛型声明 shadow 了父类泛型；`RuleAwardStockFilterNode` / `RuleLuckFilterNode` 重写了不存在于父类的 `doFilter()` 方法（死代码）；`RuleLockFilterNode` 发现锁定只打日志永远返回 ALLOW；`DefaultLogicFactory.strategyHandler` 有语法错误；`DynamicContext` 缺少 `awardId`，Lock/Stock 节点无法得知当前被抽中的奖品。

---

## 整体架构

```
RaffleService.doRaffle(ActivityFactorEntity factor)
  │
  ├─ Phase 1: activityChain.apply(factor)
  │           ActivityInfoCheckHandler → ActivitySkuStockHandler
  │
  ├─ Phase 2: @Transactional createOrderAndDeductQuota(factor)
  │           插订单 + 扣总/月/日额度
  │
  ├─ Phase 3: raffleStrategy.performRaffle(RaffleFactorEntity)
  │   ├─ Before: preRuleChain.apply(factor, new DynamicContext())
  │   │          → TAKE_OVER(awardId): 黑名单命中，直接返回
  │   │          → null + ctx.excludeAwardIds: 走权重奖池
  │   ├─ Raffle: armory.getRandomAwardId(strategyId, excludeAwardIds)
  │   └─ After:  lockNode.apply() → stockNode.apply() → luckNode.apply()
  │              每个节点: null=通过, TAKE_OVER=兜底
  │
  └─ Phase 4: updateUserRaffleOrder(orderId, awardId, awardType)
```

---

## Section 1 — 入口与活动校验链

### IRaffleService / RaffleService

```java
// 入口签名变更
RaffleResultEntity doRaffle(ActivityFactorEntity factor);
```

`RaffleFactorEntity` 在 Phase 3 内部构建：

```java
RaffleFactorEntity raffleFactor = RaffleFactorEntity.builder()
        .userId(factor.getUserId())
        .strategyId(factor.getStrategyId())
        .build();
```

### IActivityChainHandler / AbstractActivityChainHandler

链的方法签名统一改为接受 `ActivityFactorEntity`：

```java
boolean apply(ActivityFactorEntity factor);
boolean next(ActivityFactorEntity factor);
```

### ActivityFactorEntity 字段（无需新增）

| 字段 | 类型 | 用途 |
|---|---|---|
| `userId` | String | 用户标识 |
| `activityId` | Long | 活动ID，ActivityInfoCheckHandler 查活动配置 |
| `strategyId` | Long | 策略ID，ActivitySkuStockHandler 库存 key |
| `skuId` | Long | SKU ID，ActivitySkuStockHandler 精确扣减（当前按 strategyId 作 key，skuId 备用） |

---

## Section 2 — 抽奖前责任链

### StrategyPreRuleFilterFactory.DynamicContext

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public static class DynamicContext {
    private Set<Integer> excludeAwardIds = new HashSet<>();
    // 由 RuleWeightFilter 写入，由 AbstrackRaffleStrategy 读出
}
```

### 过滤器返回约定

`BusinessLinkedList` 遇到第一个非 null 结果即停止。

| 过滤器 | 通过时 | 拦截时 |
|---|---|---|
| `RuleBlacklistFilter` | return **null**（继续） | return TAKE_OVER result（停链） |
| `RuleWeightFilter` | 写入 `ctx.excludeAwardIds`，return **null** | 不适用 |

### StrategyPreRuleFilterFactory 泛型修正

```java
// 修正前（错误）：使用了 DefaultLogicFactory.DynamicContext
// 修正后
BusinessLinkedList<RaffleFactorEntity, StrategyPreRuleFilterFactory.DynamicContext, RuleFilterResultEntity>
```

### AbstrackRaffleStrategy 改造

删除 `DefaultRaffleStrategy` 中手动调 `getFilter()` 的 `doBeforeRaffleRuleFilter()` 实现，改为父类统一实现：

```java
// AbstrackRaffleStrategy 注入 BusinessLinkedList
@Resource
@Qualifier("strategyPreRuleFilter")
private BusinessLinkedList<RaffleFactorEntity,
        StrategyPreRuleFilterFactory.DynamicContext,
        RuleFilterResultEntity> preRuleChain;

protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) throws Exception {
    StrategyPreRuleFilterFactory.DynamicContext ctx =
            new StrategyPreRuleFilterFactory.DynamicContext();
    RuleFilterResultEntity result = preRuleChain.apply(factor, ctx);
    if (result != null) return result;  // 黑名单 TAKE_OVER
    return RuleFilterResultEntity.builder()
            .type(RuleFilterResultEntity.Type.ALLOW)
            .excludeAwardIds(ctx.getExcludeAwardIds())
            .build();
}
```

---

## Section 3 — 规则树

### DefaultLogicFactory.DynamicContext

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public static class DynamicContext {
    private Integer awardId;  // 本次抽中的奖品ID，Lock/Stock 节点必须
}
```

### AbstractRuleFilterService 修复

去掉 shadow 泛型声明，绑定具体类型，统一 `doApply()` 签名，删除 `doFilter()`：

```java
public abstract class AbstractRuleFilterService
        extends AbstractMultiThreadStrategyRouter<
                RaffleFactorEntity,
                DefaultLogicFactory.DynamicContext,
                RuleFilterResultEntity> {

    // 子类实现：null=通过，非null TAKE_OVER=拦截
    protected abstract RuleFilterResultEntity doApply(
            RaffleFactorEntity factory,
            DefaultLogicFactory.DynamicContext ctx) throws Exception;

    @Override
    protected void multiThread(RaffleFactorEntity factory,
            DefaultLogicFactory.DynamicContext ctx) {
        // 无多线程预加载需求，空实现
    }

    @Override
    public StrategyHandler<RaffleFactorEntity, DefaultLogicFactory.DynamicContext,
            RuleFilterResultEntity> get(RaffleFactorEntity factory,
            DefaultLogicFactory.DynamicContext ctx) {
        return null;  // 不使用 router() 模式
    }

    // 兜底结果，Lock/Stock 节点复用
    protected RuleFilterResultEntity fallback(Long strategyId) {
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.TAKE_OVER)
                .awardId(strategyRepository.queryMaxAwardId(strategyId))
                .build();
    }
}
```

> `fallback()` 需要 `IStrategyRepository`，AbstractRuleFilterService 注入 `@Resource IStrategyRepository strategyRepository`。

### 三节点 doApply() 语义

**RuleLockFilterNode**：

- 查 `rule_lock` 规则；无规则 → null
- `ctx.awardId` 在 `lockedAwardIds` 内 AND `drawCount < unlockCount` → `fallback(strategyId)`
- 否则 → null

**RuleAwardStockFilterNode**：

- `deductStock(strategyId, ctx.awardId)` 返回 false → `fallback(strategyId)`
- 返回 true → null

**RuleLuckFilterNode**：

- 查 `rule_luck` 规则；无规则 → null
- `currentLuck >= config.luckCount` → 重置运气值，return TAKE_OVER(`config.awardId`)
- 否则 → null

### DefaultLogicFactory 修复

```java
// 修复语法错误，方法加括号
public StrategyHandler<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> strategyHandler() {
    return ruleLockFilterNode;  // 树的入口节点
}
```

`@Resource` Map 改为 `Map<String, AbstractRuleFilterService>`（类型匹配）。

### DefaultRaffleStrategy.doAfterRaffleRuleFilter() 改造

删除所有内联的 Lock/Stock 逻辑与 `buildFallback()` 方法，改为顺序调用三节点：

```java
@Resource private RuleLockFilterNode       ruleLockFilterNode;
@Resource private RuleAwardStockFilterNode ruleAwardStockFilterNode;
@Resource private RuleLuckFilterNode       ruleLuckFilterNode;

@Override
protected RuleFilterResultEntity doAfterRaffleRuleFilter(
        RaffleFactorEntity factor, Integer awardId) throws Exception {

    DefaultLogicFactory.DynamicContext ctx =
            DefaultLogicFactory.DynamicContext.builder().awardId(awardId).build();

    RuleFilterResultEntity r;

    r = ruleLockFilterNode.apply(factor, ctx);
    if (r != null) return r;

    r = ruleAwardStockFilterNode.apply(factor, ctx);
    if (r != null) return r;

    r = ruleLuckFilterNode.apply(factor, ctx);
    if (r != null) return r;

    return RuleFilterResultEntity.builder()
            .type(RuleFilterResultEntity.Type.ALLOW)
            .build();
}
```

---

## 异常传播说明

`preRuleChain.apply()` 和 `node.apply()` 均声明 `throws Exception`（来自 `AbstractMultiThreadStrategyRouter`）。因此以下方法签名需同步加上 `throws Exception`：

| 方法 | 所在类 |
|---|---|
| `doBeforeRaffleRuleFilter(RaffleFactorEntity)` | `AbstrackRaffleStrategy`（base + override） |
| `doAfterRaffleRuleFilter(RaffleFactorEntity, Integer)` | `AbstrackRaffleStrategy`（base + override） |
| `performRaffle(RaffleFactorEntity)` | `IRaffleStrategy` 接口 + `AbstrackRaffleStrategy` |

---

## 变更清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `IActivityChainHandler` | 改签名 | `apply/next` 参数改为 `ActivityFactorEntity` |
| `AbstractActivityChainHandler` | 改签名 | 同上 |
| `ActivityInfoCheckHandler` | 改签名 | 从 `factor.getStrategyId()` / `factor.getActivityId()` 取值 |
| `ActivitySkuStockHandler` | 改签名 | 从 `factor.getStrategyId()` 取值 |
| `ActivityChainHandlerFactory` | 改签名 | `getChainHead().apply(factor)` |
| `IRaffleService` | 改签名 | `doRaffle(ActivityFactorEntity)` |
| `RaffleService` | 改签名 + 内部构建 `RaffleFactorEntity` | |
| `StrategyPreRuleFilterFactory` | 修泛型 + 加 `excludeAwardIds` 字段 | |
| `RuleBlacklistFilter` | 改返回值 | ALLOW→null，TAKE_OVER→非null |
| `RuleWeightFilter` | 改返回值 | 写 ctx，始终 return null |
| `AbstrackRaffleStrategy` | 注入 BusinessLinkedList，实现 `doBeforeRaffleRuleFilter` | |
| `DefaultRaffleStrategy` | 删 `doBeforeRaffleRuleFilter`，重写 `doAfterRaffleRuleFilter`，删 `buildFallback` | |
| `AbstractRuleFilterService` | 修泛型声明，加 `fallback()`，删 `doFilter` | |
| `RuleLockFilterNode` | 修 `doApply`，加真实锁定逻辑 | |
| `RuleAwardStockFilterNode` | 修 `doApply`，实现库存扣减 | |
| `RuleLuckFilterNode` | `doFilter→doApply` 签名修正 | |
| `DefaultLogicFactory` | 修语法，修 Map 类型，加 `awardId` 到 DynamicContext | |
