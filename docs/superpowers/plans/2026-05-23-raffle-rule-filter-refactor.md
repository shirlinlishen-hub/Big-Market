# Raffle Rule Filter Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复抽奖规则过滤的三处结构性问题——活动链签名统一为 ActivityFactorEntity、抽奖前责任链 DynamicContext 补 excludeAwardIds 并消除双通路冲突、规则树 DynamicContext 补 awardId 并修复节点方法签名混乱。

**Architecture:** 方案 B — AbstractRuleFilterService 节点各自实现 `doApply(factory, ctx)`，返回 `null` 表示通过，返回非 null TAKE_OVER 表示拦截兜底。DefaultRaffleStrategy 顺序调用三节点。抽奖前责任链通过 BusinessLinkedList 统一调用，Blacklist 返回 null(ALLOW)/非null(TAKE_OVER)，Weight 把结果写入 DynamicContext 后返回 null。

**Tech Stack:** Java 17, Spring Boot 3.4.3, Lombok, fastjson2, JUnit 5, Mockito (via spring-boot-starter-test)

---

## File Map

| 操作 | 文件 |
|---|---|
| 修改 | `Big-Market-domain/pom.xml` — 添加测试依赖 |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/AbstractRuleFilterService.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Factory/DefaultLogicFactory.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNode.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNode.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNode.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Factory/StrategyPreRuleFilterFactory.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilter.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilter.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/AbstrackRaffleStrategy.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleStrategy.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/DefaultRaffleStrategy.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/IActivityChainHandler.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/AbstractActivityChainHandler.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivityInfoCheckHandler.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivitySkuStockHandler.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleService.java` |
| 修改 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java` |
| 创建 | `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNodeTest.java` |
| 创建 | `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNodeTest.java` |
| 创建 | `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNodeTest.java` |
| 创建 | `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilterTest.java` |
| 创建 | `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilterTest.java` |
| 可选删除 | `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/IStrategyLogicFilterService.java` — 重构后无引用 |

---

## Task 1: 添加测试依赖

**Files:**
- Modify: `Big-Market-domain/pom.xml`

- [ ] **Step 1: 在 Big-Market-domain/pom.xml 的 `<dependencies>` 中添加**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 验证依赖可以解析**

```bash
mvn dependency:resolve -pl Big-Market-domain -q
```

Expected: BUILD SUCCESS，无报错

- [ ] **Step 3: 提交**

```bash
git add Big-Market-domain/pom.xml
git commit -m "build: add spring-boot-starter-test to domain module"
```

---

## Task 2: 修复 AbstractRuleFilterService

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/AbstractRuleFilterService.java`

当前问题：泛型声明 shadow 了父类泛型，导致节点继承时类型混乱；无 `fallback()` 方法；`doFilter()` 方法不在父类中。

- [ ] **Step 1: 替换 AbstractRuleFilterService 全部内容**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree;

import jakarta.annotation.Resource;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;
import shirlin.ai.types.design.tree.AbstractMultiThreadStrategyRouter;
import shirlin.ai.types.design.tree.StrategyHandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractRuleFilterService
        extends AbstractMultiThreadStrategyRouter<
        RaffleFactorEntity,
        DefaultLogicFactory.DynamicContext,
        RuleFilterResultEntity> {

    @Resource
    protected IStrategyRepository strategyRepository;

    @Override
    protected abstract RuleFilterResultEntity doApply(
            RaffleFactorEntity factory,
            DefaultLogicFactory.DynamicContext ctx) throws Exception;

    @Override
    protected void multiThread(RaffleFactorEntity factory,
                               DefaultLogicFactory.DynamicContext ctx)
            throws ExecutionException, InterruptedException, TimeoutException {
        // 无多线程预加载需求
    }

    @Override
    public StrategyHandler<RaffleFactorEntity, DefaultLogicFactory.DynamicContext,
            RuleFilterResultEntity> get(RaffleFactorEntity factory,
                                        DefaultLogicFactory.DynamicContext ctx) {
        return null; // 不使用 router() 模式
    }

    /**
     * 兜底结果：取最大概率奖品，Lock/Stock 节点拦截时调用。
     */
    protected RuleFilterResultEntity fallback(Long strategyId) {
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.TAKE_OVER)
                .awardId(strategyRepository.queryMaxAwardId(strategyId))
                .build();
    }
}
```

- [ ] **Step 2: 编译验证（此时节点尚未修复，编译报错属预期；只要 AbstractRuleFilterService 本身无错即可）**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "AbstractRuleFilterService"
```

Expected: 无关于 AbstractRuleFilterService 自身的编译错误

- [ ] **Step 3: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/AbstractRuleFilterService.java
git commit -m "refactor: fix AbstractRuleFilterService generic shadowing and add fallback()"
```

---

## Task 3: 修复 DefaultLogicFactory

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Factory/DefaultLogicFactory.java`

当前问题：`DynamicContext` 缺 `awardId`；`strategyHandler` 语法错误（缺括号）；显式构造器与 `@Resource` 字段注入冲突；`filterMap` 类型为 `IStrategyLogicFilterService` 与实际节点类型不匹配。

- [ ] **Step 1: 替换 DefaultLogicFactory 全部内容**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory;

import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node.RuleLockFilterNode;
import shirlin.ai.types.design.tree.StrategyHandler;
import shirlin.ai.types.exception.AppException;

import java.util.Map;

@Slf4j
@Service
public class DefaultLogicFactory {

    @Resource
    private RuleLockFilterNode ruleLockFilterNode;

    @Autowired
    private Map<String, AbstractRuleFilterService> filterMap;

    /** 规则树入口节点 */
    public StrategyHandler<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> strategyHandler() {
        return ruleLockFilterNode;
    }

    public AbstractRuleFilterService getFilter(String ruleBeanName) {
        AbstractRuleFilterService filter = filterMap.get(ruleBeanName);
        if (filter == null) {
            throw new AppException("未找到规则过滤器: " + ruleBeanName);
        }
        return filter;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        /** 本次抽中的奖品ID，Lock/Stock 节点检查时必须 */
        private Integer awardId;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "DefaultLogicFactory"
```

Expected: 无关于 DefaultLogicFactory 的编译错误

- [ ] **Step 3: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Factory/DefaultLogicFactory.java
git commit -m "fix: DefaultLogicFactory DynamicContext add awardId, fix strategyHandler syntax, fix filterMap type"
```

---

## Task 4: TDD — RuleLockFilterNode

**Files:**
- Create: `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNodeTest.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNode.java`

- [ ] **Step 1: 创建测试文件**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleLockFilterNodeTest {

    @Mock
    private IStrategyRepository strategyRepository;

    @InjectMocks
    private RuleLockFilterNode node;

    private RaffleFactorEntity factor;
    private DefaultLogicFactory.DynamicContext ctx;

    @BeforeEach
    void setUp() {
        factor = RaffleFactorEntity.builder().userId("user1").strategyId(100L).build();
        ctx = DefaultLogicFactory.DynamicContext.builder().awardId(101).build();
    }

    @Test
    void apply_noLockRule_returnsNull() throws Exception {
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_lock")).thenReturn(null);
        assertNull(node.apply(factor, ctx));
    }

    @Test
    void apply_awardNotInLockedList_returnsNull() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"unlockCount\":10,\"lockedAwardIds\":[200,300]}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_lock")).thenReturn(rule);
        when(strategyRepository.queryUserDrawCount("user1", 100L)).thenReturn(5);

        assertNull(node.apply(factor, ctx)); // awardId=101 不在 [200,300]
    }

    @Test
    void apply_awardLockedAndCountInsufficient_returnsFallback() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"unlockCount\":10,\"lockedAwardIds\":[101,102]}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_lock")).thenReturn(rule);
        when(strategyRepository.queryUserDrawCount("user1", 100L)).thenReturn(5);
        when(strategyRepository.queryMaxAwardId(100L)).thenReturn(9999);

        RuleFilterResultEntity result = node.apply(factor, ctx);

        assertNotNull(result);
        assertEquals(RuleFilterResultEntity.Type.TAKE_OVER, result.getType());
        assertEquals(9999, result.getAwardId());
    }

    @Test
    void apply_awardLockedButCountSufficient_returnsNull() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"unlockCount\":10,\"lockedAwardIds\":[101,102]}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_lock")).thenReturn(rule);
        when(strategyRepository.queryUserDrawCount("user1", 100L)).thenReturn(15); // >= 10

        assertNull(node.apply(factor, ctx));
    }
}
```

- [ ] **Step 2: 运行测试确认全部失败**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleLockFilterNodeTest -q 2>&1 | tail -5
```

Expected: 编译失败或 FAIL（`doApply` 签名不对）

- [ ] **Step 3: 替换 RuleLockFilterNode 实现**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

@Slf4j
@Service("ruleLockFilterNode")
public class RuleLockFilterNode extends AbstractRuleFilterService {

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity factory,
                                             DefaultLogicFactory.DynamicContext ctx) throws Exception {

        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULELOCK.getRuleModel());
        if (rule == null) return null;

        RuleLockConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleLockConfigEntity.class);
        int drawCount = strategyRepository.queryUserDrawCount(
                factory.getUserId(), factory.getStrategyId());

        boolean locked = config.getLockedAwardIds() != null
                && config.getLockedAwardIds().contains(ctx.getAwardId())
                && drawCount < config.getUnlockCount();

        if (locked) {
            log.info("Lock 拦截 userId:{} awardId:{} drawCount:{}/{}",
                    factory.getUserId(), ctx.getAwardId(), drawCount, config.getUnlockCount());
            return fallback(factory.getStrategyId());
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试确认全部通过**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleLockFilterNodeTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNode.java \
        Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLockFilterNodeTest.java
git commit -m "fix: RuleLockFilterNode implement real lock logic with ctx.awardId"
```

---

## Task 5: TDD — RuleAwardStockFilterNode

**Files:**
- Create: `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNodeTest.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNode.java`

- [ ] **Step 1: 创建测试文件**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleAwardStockFilterNodeTest {

    @Mock
    private IStrategyRepository strategyRepository;

    @InjectMocks
    private RuleAwardStockFilterNode node;

    private RaffleFactorEntity factor;
    private DefaultLogicFactory.DynamicContext ctx;

    @BeforeEach
    void setUp() {
        factor = RaffleFactorEntity.builder().userId("user1").strategyId(100L).build();
        ctx = DefaultLogicFactory.DynamicContext.builder().awardId(101).build();
    }

    @Test
    void apply_stockAvailable_returnsNull() throws Exception {
        when(strategyRepository.deductStock(100L, 101)).thenReturn(true);
        assertNull(node.apply(factor, ctx));
    }

    @Test
    void apply_stockDepleted_returnsFallback() throws Exception {
        when(strategyRepository.deductStock(100L, 101)).thenReturn(false);
        when(strategyRepository.queryMaxAwardId(100L)).thenReturn(9999);

        RuleFilterResultEntity result = node.apply(factor, ctx);

        assertNotNull(result);
        assertEquals(RuleFilterResultEntity.Type.TAKE_OVER, result.getType());
        assertEquals(9999, result.getAwardId());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleAwardStockFilterNodeTest -q 2>&1 | tail -5
```

Expected: 编译失败或 FAIL（`doFilter` 方法不存在）

- [ ] **Step 3: 替换 RuleAwardStockFilterNode 实现**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

@Slf4j
@Service("ruleAwardStockFilterNode")
public class RuleAwardStockFilterNode extends AbstractRuleFilterService {

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity factory,
                                             DefaultLogicFactory.DynamicContext ctx) throws Exception {

        boolean stockOk = strategyRepository.deductStock(
                factory.getStrategyId(), ctx.getAwardId());
        if (!stockOk) {
            log.info("Stock 耗尽 strategyId:{} awardId:{}",
                    factory.getStrategyId(), ctx.getAwardId());
            return fallback(factory.getStrategyId());
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleAwardStockFilterNodeTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNode.java \
        Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleAwardStockFilterNodeTest.java
git commit -m "fix: RuleAwardStockFilterNode implement real stock deduction via ctx.awardId"
```

---

## Task 6: TDD — RuleLuckFilterNode

**Files:**
- Create: `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNodeTest.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNode.java`

- [ ] **Step 1: 创建测试文件**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleLuckFilterNodeTest {

    @Mock
    private IStrategyRepository strategyRepository;

    @InjectMocks
    private RuleLuckFilterNode node;

    private RaffleFactorEntity factor;
    private DefaultLogicFactory.DynamicContext ctx;

    @BeforeEach
    void setUp() {
        factor = RaffleFactorEntity.builder().userId("user1").strategyId(100L).build();
        ctx = DefaultLogicFactory.DynamicContext.builder().awardId(101).build();
    }

    @Test
    void apply_noLuckRule_returnsNull() throws Exception {
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_luck")).thenReturn(null);
        assertNull(node.apply(factor, ctx));
    }

    @Test
    void apply_luckBelowThreshold_returnsNull() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"luckCount\":10,\"awardId\":8888}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_luck")).thenReturn(rule);
        when(strategyRepository.queryUserLuckValue("user1", 100L)).thenReturn(5);

        assertNull(node.apply(factor, ctx));
    }

    @Test
    void apply_luckReachesThreshold_returnsLuckAwardAndResetsValue() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"luckCount\":10,\"awardId\":8888}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_luck")).thenReturn(rule);
        when(strategyRepository.queryUserLuckValue("user1", 100L)).thenReturn(10);

        RuleFilterResultEntity result = node.apply(factor, ctx);

        assertNotNull(result);
        assertEquals(RuleFilterResultEntity.Type.TAKE_OVER, result.getType());
        assertEquals(8888, result.getAwardId());
        verify(strategyRepository).resetUserLuckValue("user1", 100L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleLuckFilterNodeTest -q 2>&1 | tail -5
```

Expected: 编译失败或 FAIL（`doFilter` 签名错误）

- [ ] **Step 3: 替换 RuleLuckFilterNode 实现**

```java
package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

@Slf4j
@Service("ruleLuckFilterNode")
public class RuleLuckFilterNode extends AbstractRuleFilterService {

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity factory,
                                             DefaultLogicFactory.DynamicContext ctx) throws Exception {

        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULELUCK.getRuleModel());
        if (rule == null) return null;

        RuleLuckConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleLuckConfigEntity.class);
        int currentLuck = strategyRepository.queryUserLuckValue(
                factory.getUserId(), factory.getStrategyId());

        if (currentLuck >= config.getLuckCount()) {
            strategyRepository.resetUserLuckValue(factory.getUserId(), factory.getStrategyId());
            log.info("运气值兜底 userId:{} luck:{}", factory.getUserId(), currentLuck);
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.TAKE_OVER)
                    .awardId(config.getAwardId())
                    .build();
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -pl Big-Market-domain -Dtest=RuleLuckFilterNodeTest -q 2>&1 | tail -5
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNode.java \
        Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Tree/Node/RuleLuckFilterNodeTest.java
git commit -m "fix: RuleLuckFilterNode rename doFilter to doApply"
```

---

## Task 7: 修复 StrategyPreRuleFilterFactory

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Factory/StrategyPreRuleFilterFactory.java`

当前问题：`DynamicContext` 为空（缺 `excludeAwardIds`）；chain 泛型里用了 `DefaultLogicFactory.DynamicContext`（类型错误）。

- [ ] **Step 1: 替换 StrategyPreRuleFilterFactory 全部内容**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory;

import cn.bugstack.wrench.design.framework.link.model2.LinkArmory;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter.RuleBlacklistFilter;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter.RuleWeightFilter;
import shirlin.ai.types.design.link.model2.chain.BusinessLinkedList;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class StrategyPreRuleFilterFactory {

    @Resource
    private RuleBlacklistFilter ruleBlacklistFilter;

    @Resource
    private RuleWeightFilter ruleWeightFilter;

    @Bean("strategyPreRuleFilter")
    public BusinessLinkedList<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> strategyPreRuleHandler() {
        LinkArmory<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> linkArmory =
                new LinkArmory<>("抽奖前规则过滤链", ruleBlacklistFilter, ruleWeightFilter);
        return linkArmory.getLogicLink();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        /** 由 RuleWeightFilter 写入，由 AbstrackRaffleStrategy 读出传给 getRandomAwardId() */
        @Builder.Default
        private Set<Integer> excludeAwardIds = new HashSet<>();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "StrategyPreRuleFilterFactory"
```

Expected: 无关于该类的编译错误

- [ ] **Step 3: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Factory/StrategyPreRuleFilterFactory.java
git commit -m "fix: StrategyPreRuleFilterFactory DynamicContext add excludeAwardIds, fix generic type"
```

---

## Task 8: TDD — RuleBlacklistFilter + RuleWeightFilter

**Files:**
- Create: `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilterTest.java`
- Create: `Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilterTest.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilter.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilter.java`

BusinessLinkedList 在第一个非 null 结果时停链。ALLOW 必须返回 null，否则 Weight 过滤器永远无法执行。

- [ ] **Step 1: 创建 RuleBlacklistFilterTest**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleBlacklistFilterTest {

    @Mock
    private IStrategyRepository strategyRepository;

    @InjectMocks
    private RuleBlacklistFilter filter;

    private RaffleFactorEntity factor;
    private StrategyPreRuleFilterFactory.DynamicContext ctx;

    @BeforeEach
    void setUp() {
        factor = RaffleFactorEntity.builder().userId("user1").strategyId(100L).build();
        ctx = new StrategyPreRuleFilterFactory.DynamicContext();
    }

    @Test
    void apply_noBlacklistRule_returnsNull() throws Exception {
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_blacklist")).thenReturn(null);
        assertNull(filter.apply(factor, ctx));
    }

    @Test
    void apply_userNotInBlacklist_returnsNull() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"awardId\":9999,\"userBlacklist\":\"user2,user3\"}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_blacklist")).thenReturn(rule);
        assertNull(filter.apply(factor, ctx)); // user1 不在黑名单
    }

    @Test
    void apply_userInBlacklist_returnsTakeOver() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"awardId\":9999,\"userBlacklist\":\"user1,user2\"}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_blacklist")).thenReturn(rule);

        RuleFilterResultEntity result = filter.apply(factor, ctx);

        assertNotNull(result);
        assertEquals(RuleFilterResultEntity.Type.TAKE_OVER, result.getType());
        assertEquals(9999, result.getAwardId());
    }
}
```

- [ ] **Step 2: 创建 RuleWeightFilterTest**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleWeightFilterTest {

    @Mock
    private IStrategyRepository strategyRepository;

    @InjectMocks
    private RuleWeightFilter filter;

    private RaffleFactorEntity factor;
    private StrategyPreRuleFilterFactory.DynamicContext ctx;

    @BeforeEach
    void setUp() {
        factor = RaffleFactorEntity.builder().userId("user1").strategyId(100L).build();
        ctx = new StrategyPreRuleFilterFactory.DynamicContext();
    }

    @Test
    void apply_noWeightRule_returnsNullWithEmptyExclude() throws Exception {
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_weight")).thenReturn(null);

        assertNull(filter.apply(factor, ctx));
        assertTrue(ctx.getExcludeAwardIds().isEmpty());
    }

    @Test
    void apply_userBelowAllThresholds_returnsNullWithEmptyExclude() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"4000\":[102,103],\"6000\":[101,102,103]}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_weight")).thenReturn(rule);
        when(strategyRepository.queryUserWeightValue("user1", 100L)).thenReturn(1000); // 低于4000

        assertNull(filter.apply(factor, ctx));
        assertTrue(ctx.getExcludeAwardIds().isEmpty());
    }

    @Test
    void apply_userMeetsThreshold_returnsNullAndPopulatesExclude() throws Exception {
        StrategyRuleEntity rule = StrategyRuleEntity.builder()
                .ruleValue("{\"4000\":[102,103],\"6000\":[101,102,103]}")
                .build();
        when(strategyRepository.queryStrategyRuleByModel(100L, "rule_weight")).thenReturn(rule);
        when(strategyRepository.queryUserWeightValue("user1", 100L)).thenReturn(5000); // 满足4000但不满足6000
        when(strategyRepository.queryStrategyAwardListById(100L)).thenReturn(
                Arrays.asList(
                        StrategyAwardEntity.builder().awardId(101).build(),
                        StrategyAwardEntity.builder().awardId(102).build(),
                        StrategyAwardEntity.builder().awardId(103).build()
                ));

        assertNull(filter.apply(factor, ctx));
        // 权重4000只有[102,103]，所以101被排除
        assertTrue(ctx.getExcludeAwardIds().contains(101));
        assertFalse(ctx.getExcludeAwardIds().contains(102));
        assertFalse(ctx.getExcludeAwardIds().contains(103));
    }
}
```

- [ ] **Step 3: 运行两个测试确认失败**

```bash
mvn test -pl Big-Market-domain -Dtest="RuleBlacklistFilterTest,RuleWeightFilterTest" -q 2>&1 | tail -5
```

Expected: FAIL（ALLOW 分支当前返回非 null，导致链提前终止）

- [ ] **Step 4: 修改 RuleBlacklistFilter — ALLOW 分支改返回 null**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleBlacklistConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;
import shirlin.ai.types.design.link.model2.handler.ILogicHandler;

@Slf4j
@Service
public class RuleBlacklistFilter implements ILogicHandler<RaffleFactorEntity,
        StrategyPreRuleFilterFactory.DynamicContext, RuleFilterResultEntity> {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public RuleFilterResultEntity apply(RaffleFactorEntity factory,
                                        StrategyPreRuleFilterFactory.DynamicContext dynamicContext) throws Exception {

        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULEBLACKLIST.getRuleModel());
        if (rule == null || rule.getRuleValue() == null) return null; // ALLOW → 继续链

        RuleBlacklistConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleBlacklistConfigEntity.class);
        if (config.getUserBlacklist() != null && !config.getUserBlacklist().isBlank()) {
            for (String blackUser : config.getUserBlacklist().split(",")) {
                if (factory.getUserId().equals(blackUser.trim())) {
                    log.info("黑名单命中 userId:{} strategyId:{}", factory.getUserId(), factory.getStrategyId());
                    return RuleFilterResultEntity.builder()
                            .type(RuleFilterResultEntity.Type.TAKE_OVER)
                            .awardId(config.getAwardId())
                            .build(); // TAKE_OVER → 停链
                }
            }
        }
        return null; // ALLOW → 继续链
    }
}
```

- [ ] **Step 5: 修改 RuleWeightFilter — 写入 ctx，始终返回 null**

```java
package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;
import shirlin.ai.types.design.link.model2.handler.ILogicHandler;

import java.util.*;

@Slf4j
@Service
public class RuleWeightFilter implements ILogicHandler<RaffleFactorEntity,
        StrategyPreRuleFilterFactory.DynamicContext, RuleFilterResultEntity> {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public RuleFilterResultEntity apply(RaffleFactorEntity factory,
                                        StrategyPreRuleFilterFactory.DynamicContext ctx) throws Exception {

        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULEWEIGHT.getRuleModel());
        if (rule == null || rule.getRuleValue() == null) {
            ctx.setExcludeAwardIds(Collections.emptySet());
            return null; // ALLOW → 继续链
        }

        Map<String, List<Integer>> weightMap = JSON.parseObject(
                rule.getRuleValue(), new TypeReference<Map<String, List<Integer>>>() {
                });
        int userWeightValue = strategyRepository.queryUserWeightValue(
                factory.getUserId(), factory.getStrategyId());

        List<Integer> allowedAwardIds = null;
        int highestThreshold = 0;
        for (Map.Entry<String, List<Integer>> entry : weightMap.entrySet()) {
            int threshold = Integer.parseInt(entry.getKey());
            if (userWeightValue >= threshold && threshold > highestThreshold) {
                highestThreshold = threshold;
                allowedAwardIds = entry.getValue();
            }
        }

        if (allowedAwardIds == null) {
            ctx.setExcludeAwardIds(Collections.emptySet());
            return null; // 未达任何阈值，走全奖池
        }

        List<StrategyAwardEntity> allAwards = strategyRepository.queryStrategyAwardListById(factory.getStrategyId());
        Set<Integer> excludeAwardIds = new HashSet<>();
        for (StrategyAwardEntity award : allAwards) {
            if (!allowedAwardIds.contains(award.getAwardId())) {
                excludeAwardIds.add(award.getAwardId());
            }
        }

        log.info("权重命中 userId:{} threshold:{} excludeCount:{}",
                factory.getUserId(), highestThreshold, excludeAwardIds.size());
        ctx.setExcludeAwardIds(excludeAwardIds);
        return null; // ALLOW → 继续链（结果已写入 ctx）
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

```bash
mvn test -pl Big-Market-domain -Dtest="RuleBlacklistFilterTest,RuleWeightFilterTest" -q 2>&1 | tail -5
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 7: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilter.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilter.java \
        Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleBlacklistFilterTest.java \
        Big-Market-domain/src/test/java/shirlin/ai/domain/strategy/service/Rule/Chain/Filter/RuleWeightFilterTest.java
git commit -m "fix: chain filters return null on ALLOW, write excludeAwardIds to DynamicContext"
```

---

## Task 9: 修复 AbstrackRaffleStrategy + IRaffleStrategy

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleStrategy.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/AbstrackRaffleStrategy.java`

- [ ] **Step 1: 修改 IRaffleStrategy — 添加 throws Exception**

```java
package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleStrategy {

    RaffleResultEntity performRaffle(RaffleFactorEntity factor) throws Exception;

}
```

- [ ] **Step 2: 替换 AbstrackRaffleStrategy 全部内容**

```java
package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.domain.strategy.service.IStrategyArmory;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;
import shirlin.ai.types.design.link.model2.chain.BusinessLinkedList;

import java.util.Collections;
import java.util.Set;

public abstract class AbstrackRaffleStrategy implements IRaffleStrategy {

    @Resource
    protected IStrategyRepository strategyRepository;

    @Resource
    protected IStrategyArmory armory;

    @Resource
    @Qualifier("strategyPreRuleFilter")
    private BusinessLinkedList<RaffleFactorEntity,
            StrategyPreRuleFilterFactory.DynamicContext,
            RuleFilterResultEntity> preRuleChain;

    @Override
    public RaffleResultEntity performRaffle(RaffleFactorEntity factor) throws Exception {

        Long strategyId = factor.getStrategyId();

        // 1. 前置规则过滤（责任链：Blacklist → Weight）
        RuleFilterResultEntity beforeResult = doBeforeRaffleRuleFilter(factor);
        if (beforeResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(beforeResult.getType())) {
            return buildResult(strategyId, beforeResult.getAwardId());
        }

        // 2. 执行抽奖：概率区间二分查找
        Set<Integer> excludeAwardIds = (beforeResult != null && beforeResult.getExcludeAwardIds() != null)
                ? beforeResult.getExcludeAwardIds()
                : Collections.emptySet();
        Integer awardId = armory.getRandomAwardId(strategyId, excludeAwardIds);

        // 3. 后置规则过滤（规则树：Lock → Stock → Luck）
        RuleFilterResultEntity afterResult = doAfterRaffleRuleFilter(factor, awardId);
        if (afterResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(afterResult.getType())) {
            return buildResult(strategyId, afterResult.getAwardId());
        }

        return buildResult(strategyId, awardId);
    }

    /**
     * 前置规则（责任链）：调用 BusinessLinkedList，结果写入 ctx.excludeAwardIds。
     * null 结果 + ctx 里的 excludeAwardIds 表示 ALLOW；非 null TAKE_OVER 表示黑名单命中。
     */
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) throws Exception {
        StrategyPreRuleFilterFactory.DynamicContext ctx =
                new StrategyPreRuleFilterFactory.DynamicContext();
        RuleFilterResultEntity result = preRuleChain.apply(factor, ctx);
        if (result != null) return result; // 黑名单 TAKE_OVER
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .excludeAwardIds(ctx.getExcludeAwardIds())
                .build();
    }

    /**
     * 后置规则（规则树）：子类实现，决定最终给什么奖品。
     */
    protected RuleFilterResultEntity doAfterRaffleRuleFilter(
            RaffleFactorEntity factor, Integer awardId) throws Exception {
        return null;
    }

    protected RaffleResultEntity buildResult(Long strategyId, Integer awardId) {
        StrategyAwardEntity award = strategyRepository.queryStrategyAward(strategyId, awardId);
        if (award == null) {
            return RaffleResultEntity.builder().awardId(awardId).build();
        }
        return RaffleResultEntity.builder()
                .awardId(awardId)
                .awardType(award.getAwardType())
                .sort(award.getSort())
                .build();
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "AbstrackRaffleStrategy\|IRaffleStrategy"
```

Expected: 无编译错误

- [ ] **Step 4: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleStrategy.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/AbstrackRaffleStrategy.java
git commit -m "refactor: AbstrackRaffleStrategy inject BusinessLinkedList, unify pre-filter via chain"
```

---

## Task 10: 修复 DefaultRaffleStrategy

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/DefaultRaffleStrategy.java`

删除手动调 `getFilter()` 的 `doBeforeRaffleRuleFilter()`（父类已实现），重写 `doAfterRaffleRuleFilter()` 使用三节点。

- [ ] **Step 1: 替换 DefaultRaffleStrategy 全部内容**

```java
package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node.RuleAwardStockFilterNode;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node.RuleLockFilterNode;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node.RuleLuckFilterNode;

@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstrackRaffleStrategy {

    @Resource
    private RuleLockFilterNode ruleLockFilterNode;

    @Resource
    private RuleAwardStockFilterNode ruleAwardStockFilterNode;

    @Resource
    private RuleLuckFilterNode ruleLuckFilterNode;

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
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "DefaultRaffleStrategy"
```

Expected: 无编译错误

- [ ] **Step 3: 运行所有已有测试确认无回归**

```bash
mvn test -pl Big-Market-domain -q 2>&1 | tail -8
```

Expected: 所有测试通过

- [ ] **Step 4: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/DefaultRaffleStrategy.java
git commit -m "refactor: DefaultRaffleStrategy use tree nodes for post-filter, remove inline lock/stock logic"
```

---

## Task 11: 修复活动校验链签名

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/IActivityChainHandler.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/AbstractActivityChainHandler.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivityInfoCheckHandler.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivitySkuStockHandler.java`

- [ ] **Step 1: 修改 IActivityChainHandler — 签名改为 ActivityFactorEntity**

```java
package shirlin.ai.domain.Activity.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;

public interface IActivityChainHandler {

    boolean apply(ActivityFactorEntity factor);

    IActivityChainHandler appendNext(IActivityChainHandler next);

    boolean next(ActivityFactorEntity factor);
}
```

- [ ] **Step 2: 修改 AbstractActivityChainHandler**

```java
package shirlin.ai.domain.Activity.service.Rule.Chain;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.IActivityChainHandler;

public abstract class AbstractActivityChainHandler implements IActivityChainHandler {

    private IActivityChainHandler next;

    @Override
    public IActivityChainHandler appendNext(IActivityChainHandler next) {
        this.next = next;
        return next;
    }

    @Override
    public boolean next(ActivityFactorEntity factor) {
        if (next == null) return true;
        return next.apply(factor);
    }
}
```

- [ ] **Step 3: 修改 ActivityInfoCheckHandler — 从 factor 取字段**

```java
package shirlin.ai.domain.Activity.service.Rule.Chain.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.Rule.Chain.AbstractActivityChainHandler;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.util.Date;

@Slf4j
@Service("activityInfoCheckHandler")
public class ActivityInfoCheckHandler extends AbstractActivityChainHandler {

    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean apply(ActivityFactorEntity factor) {
        Long activityId = factor.getActivityId();

        ActivityEntity activity = activityRepository.cacheGetActivity(activityId);
        if (activity == null) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }
        if (activity.getStatus() == null || activity.getStatus() != 1) {
            log.warn("活动状态异常 activityId:{} status:{}", activityId, activity.getStatus());
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }

        Date now = new Date();
        if (activity.getBeginTime() != null && now.before(activity.getBeginTime())) {
            log.warn("活动未开始 activityId:{}", activityId);
            throw new AppException(ResponseCode.STRATEGY_NOT_ACTIVE.getInfo());
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            log.warn("活动已结束 activityId:{}", activityId);
            throw new AppException(ResponseCode.ACTIVITY_EXPIRED.getInfo());
        }

        return next(factor);
    }
}
```

- [ ] **Step 4: 修改 ActivitySkuStockHandler**

```java
package shirlin.ai.domain.Activity.service.Rule.Chain.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.Rule.Chain.AbstractActivityChainHandler;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

@Slf4j
@Service("activitySkuStockHandler")
public class ActivitySkuStockHandler extends AbstractActivityChainHandler {

    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean apply(ActivityFactorEntity factor) {
        boolean stockOk = activityRepository.deductActivitySkuStock(factor.getStrategyId());
        if (!stockOk) {
            log.warn("SKU 库存不足 strategyId:{}", factor.getStrategyId());
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_EMPTY.getInfo());
        }
        return next(factor);
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl Big-Market-domain -am -q 2>&1 | grep "ActivityChainHandler\|ActivityInfoCheck\|ActivitySkuStock"
```

Expected: 无编译错误

- [ ] **Step 6: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/IActivityChainHandler.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/AbstractActivityChainHandler.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivityInfoCheckHandler.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivitySkuStockHandler.java
git commit -m "refactor: activity chain signature changed to ActivityFactorEntity"
```

---

## Task 12: 修复 IRaffleService + RaffleService 入口

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleService.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java`

- [ ] **Step 1: 修改 IRaffleService**

```java
package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleService {

    RaffleResultEntity doRaffle(ActivityFactorEntity factor) throws Exception;

    Long createOrderAndDeductQuota(ActivityFactorEntity factor);
}
```

- [ ] **Step 2: 替换 RaffleService 全部内容**

```java
package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.Rule.Chain.ActivityChainHandlerFactory;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
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

    /** 自注入确保 @Transactional 通过 Spring AOP 代理生效 */
    @Resource
    private RaffleService self;

    @Override
    public RaffleResultEntity doRaffle(ActivityFactorEntity factor) throws Exception {

        // Phase 1: 活动校验责任链
        activityChainHandlerFactory.getChainHead().apply(factor);

        // Phase 2: 事务内操作（创建订单 + 扣三层额度）
        Long orderId = self.createOrderAndDeductQuota(factor);

        // Phase 3: 执行抽奖（策略链 + 规则树）
        RaffleFactorEntity raffleFactor = RaffleFactorEntity.builder()
                .userId(factor.getUserId())
                .strategyId(factor.getStrategyId())
                .build();
        RaffleResultEntity result = raffleStrategy.performRaffle(raffleFactor);

        // Phase 4: 回填订单
        activityRepository.updateUserRaffleOrder(orderId, result.getAwardId(), result.getAwardType());

        log.info("抽奖完成 userId:{} strategyId:{} awardId:{}",
                factor.getUserId(), factor.getStrategyId(), result.getAwardId());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOrderAndDeductQuota(ActivityFactorEntity factor) {
        Long orderId = activityRepository.createUserRaffleOrder(
                factor.getUserId(), factor.getStrategyId());

        if (!activityRepository.deductUserTotalQuota(factor.getUserId(), factor.getStrategyId())) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }
        if (!activityRepository.deductUserMonthlyQuota(factor.getUserId(), factor.getStrategyId())) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }
        if (!activityRepository.deductUserDailyQuota(factor.getUserId(), factor.getStrategyId())) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        return orderId;
    }
}
```

- [ ] **Step 3: 全量编译验证（包括 app 模块）**

```bash
mvn compile -pl Big-Market-app -am -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

如果有 trigger 层（HTTP 入口）调用 `doRaffle(userId, strategyId)`，需同步修改调用处，传入 `ActivityFactorEntity`。搜索方式：

```bash
grep -r "doRaffle" Big-Market-trigger/src --include="*.java" -n
```

- [ ] **Step 4: 运行所有测试**

```bash
mvn test -pl Big-Market-domain -q 2>&1 | tail -8
```

Expected: 所有测试通过，无 FAIL / ERROR

- [ ] **Step 5: 提交**

```bash
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IRaffleService.java \
        Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java
git commit -m "refactor: RaffleService entry changed to ActivityFactorEntity, wire all four phases"
```

---

## 自检

**Spec 覆盖：**
- [x] Section 1 活动链签名 → Task 11+12
- [x] Section 2 DynamicContext excludeAwardIds → Task 7+8
- [x] Section 2 过滤器 null/非null 约定 → Task 8
- [x] Section 2 AbstrackRaffleStrategy 注入 BusinessLinkedList → Task 9
- [x] Section 3 DynamicContext awardId → Task 3
- [x] Section 3 AbstractRuleFilterService 修复 → Task 2
- [x] Section 3 三节点 doApply 修复 → Task 4+5+6
- [x] Section 3 DefaultLogicFactory syntax → Task 3
- [x] Section 3 DefaultRaffleStrategy 顺序调用 → Task 10
- [x] 异常传播 throws Exception → Task 9

**类型一致性：**
- `DynamicContext.awardId` (Integer) — Task 3 定义，Task 4/5/6/10 使用 ✓
- `DynamicContext.excludeAwardIds` (Set\<Integer\>) — Task 7 定义，Task 8/9 使用 ✓
- `AbstractRuleFilterService.fallback()` — Task 2 定义，Task 4/5 使用 ✓
- `ActivityFactorEntity` — Task 11 使用，Task 12 使用 ✓
