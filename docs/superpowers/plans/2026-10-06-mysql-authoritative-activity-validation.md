# MySQL Authoritative Configuration and Unified Activity Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make purchase, draw, and startup use one activity validity check, and make MySQL the only runtime source for activity and strategy configuration.

**Architecture:** Add a domain service that validates an activity with an injected clock and returns the validated entity to all callers. Refactor `StrategyArmory` to load and validate an immutable strategy definition from MySQL for every draw, then remove the obsolete Redis cache API, implementation, configuration, and dependencies.

**Tech Stack:** Java 21, Spring Boot, MyBatis, JUnit 5, Mockito, Maven

**Spec:** `docs/superpowers/specs/2026-10-06-mysql-authoritative-activity-validation-design.md`

## Global Constraints

- MySQL remains the only authority for activities, strategies, awards, rules, and probability configuration.
- Do not add JVM, Redis, or distributed configuration caches in this change.
- Keep the existing half-open probability ranges and require complete default and weight pools to total exactly `1`.
- Keep the explicit unlimited-stock fallback award requirement.
- Activity start and end boundaries are inclusive.
- Invalid activities must be rejected before draw quota reservation.
- Inventory buckets, Outbox, RabbitMQ, refunds, rebates, and award fulfillment behavior must remain unchanged.
- Coupon and physical awards continue through `ManualAwardFulfillmentHandler`.
- Before every Maven command, set `JAVA_HOME` to `C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot` and prepend `$env:JAVA_HOME/bin` to `Path` in the same PowerShell session.

## Review Focus

- An active activity whose `strategyId` is null must fail before quota reservation; Task 2 adds this assertion.
- Start and end instants are inclusive while one millisecond outside is rejected; Task 1 tests all four boundaries.
- A configured weight group that disappears between rule selection and pool loading must fail rather than use the default pool; Task 3 tests an unknown group ID.
- MySQL probabilities changed between two requests must affect the second request without an assembly call; Task 3 uses deterministic 100% pools to prove this.
- Removing all awards through exclusions must fail without consuming a random ticket; Task 3 tests the empty exclusion pool.

---

### Task 1: Add the canonical activity eligibility service

**Files:**
- Create: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/ActivityEligibilityService.java`
- Create: `Big-Market-app/src/main/java/shirlin/ai/config/ClockConfig.java`
- Create: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/ActivityEligibilityServiceTest.java`

**Interfaces:**
- Consumes: `IActivityRepository.queryActivityById(Long activityId)` and `java.time.Clock`.
- Produces: `ActivityEntity ActivityEligibilityService.requireActive(Long activityId)` for Tasks 2 and 5.

- [ ] **Step 1: Write the failing activity eligibility tests**

Create a JUnit 5 test that fixes the current instant and verifies every validation branch. The key setup and assertions are:

```java
@ExtendWith(MockitoExtension.class)
class ActivityEligibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T04:00:00Z");

    @Mock
    private IActivityRepository repository;

    private ActivityEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityEligibilityService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsNullActivityId() {
        AppException error = assertThrows(AppException.class,
                () -> service.requireActive(null));
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), error.getCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingOrDisabledActivity() {
        when(repository.queryActivityById(1L)).thenReturn(null);
        assertCode(ResponseCode.ACTIVITY_NOT_EXISTS,
                () -> service.requireActive(1L));

        when(repository.queryActivityById(2L)).thenReturn(activity(2L, 0, null, null));
        assertCode(ResponseCode.ACTIVITY_NOT_EXISTS,
                () -> service.requireActive(2L));
    }

    @Test
    void startAndEndBoundariesAreInclusive() {
        Date now = Date.from(NOW);
        when(repository.queryActivityById(1L))
                .thenReturn(activity(1L, 1, now, now));
        assertSame(repository.queryActivityById(1L), service.requireActive(1L));
    }

    @Test
    void rejectsBeforeStartAndAfterEnd() {
        when(repository.queryActivityById(1L)).thenReturn(activity(
                1L, 1, Date.from(NOW.plusMillis(1)), null));
        assertCode(ResponseCode.STRATEGY_NOT_ACTIVE,
                () -> service.requireActive(1L));

        when(repository.queryActivityById(2L)).thenReturn(activity(
                2L, 1, null, Date.from(NOW.minusMillis(1))));
        assertCode(ResponseCode.ACTIVITY_EXPIRED,
                () -> service.requireActive(2L));
    }

    @Test
    void acceptsOpenEndedActiveActivity() {
        ActivityEntity activity = activity(1L, 1, null, null);
        when(repository.queryActivityById(1L)).thenReturn(activity);
        assertSame(activity, service.requireActive(1L));
    }
}
```

Implement `activity(...)` to build an `ActivityEntity` with `strategyId=100001L`, and implement `assertCode(...)` to compare `AppException.code`. Do not mock time.

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
mvn -o -pl Big-Market-app -am '-Dtest=ActivityEligibilityServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation fails because `ActivityEligibilityService` does not exist.

- [ ] **Step 3: Implement the service and production clock**

Create the service with constructor injection:

```java
@Service
public class ActivityEligibilityService {
    private final IActivityRepository repository;
    private final Clock clock;

    public ActivityEligibilityService(IActivityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ActivityEntity requireActive(Long activityId) {
        if (activityId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }
        ActivityEntity activity = repository.queryActivityById(activityId);
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
        }
        Date now = Date.from(clock.instant());
        if (activity.getBeginTime() != null && now.before(activity.getBeginTime())) {
            throw new AppException(ResponseCode.STRATEGY_NOT_ACTIVE.getCode());
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            throw new AppException(ResponseCode.ACTIVITY_EXPIRED.getCode());
        }
        return activity;
    }
}
```

Create the configuration bean:

```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command again.

Expected: all `ActivityEligibilityServiceTest` cases pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/ActivityEligibilityService.java Big-Market-app/src/main/java/shirlin/ai/config/ClockConfig.java Big-Market-app/src/test/java/shirlin/ai/test/Domain/ActivityEligibilityServiceTest.java
git commit -m "feat: centralize activity eligibility validation"
```

---

### Task 2: Route purchase, draw, and assembly through the canonical validator

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Sku/ActivitySkuService.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Raffle/RaffleService.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Armory/ActivityAmory.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/adapter/repository/IActivityRepository.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/ActivityRepository.java`
- Delete: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/IActivityChainHandler.java`
- Delete: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/AbstractActivityChainHandler.java`
- Delete: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/Impl/ActivityInfoCheckHandler.java`
- Delete: `Big-Market-domain/src/main/java/shirlin/ai/domain/Activity/service/Rule/Chain/ActivityChainHandlerFactory.java`
- Create: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/ActivityValidationEntryTest.java`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/ActivitySkuPurchaseTest.java`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/RaffleFailureCompensationTest.java`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/RaffleReplayTest.java`

**Interfaces:**
- Consumes: `ActivityEligibilityService.requireActive(Long)` from Task 1.
- Produces: one active call path for purchase, draw, and startup activity checks; no activity cache methods remain.

- [ ] **Step 1: Write failing entry-path tests**

Create tests with mocked collaborators. Pin the two behaviors that previously diverged:

```java
@Test
void raffleUsesValidatedStrategyAndDoesNotReserveWhenActivityIsInvalid() {
    ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
    RaffleOrderService orders = mock(RaffleOrderService.class);
    RaffleService service = new RaffleService();
    ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
    ReflectionTestUtils.setField(service, "raffleOrderService", orders);

    ActivityFactorEntity factor = factor();
    when(eligibility.requireActive(20001L))
            .thenThrow(new AppException(ResponseCode.ACTIVITY_EXPIRED.getCode()));

    assertThrows(AppException.class, () -> service.doRaffle(factor));
    verifyNoInteractions(orders);
}

@Test
void raffleRejectsActiveActivityWithoutStrategyBeforeReservation() {
    ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
    RaffleOrderService orders = mock(RaffleOrderService.class);
    RaffleService service = new RaffleService();
    ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
    ReflectionTestUtils.setField(service, "raffleOrderService", orders);
    when(eligibility.requireActive(20001L)).thenReturn(
            ActivityEntity.builder().activityId(20001L).status(1).build());

    assertThrows(AppException.class, () -> service.doRaffle(factor()));
    verifyNoInteractions(orders);
}

@Test
void purchaseBuildsAggregateFromCanonicalValidatedActivity() {
    IActivityRepository repository = mock(IActivityRepository.class);
    ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
    ActivitySkuService service = new ActivitySkuService();
    ReflectionTestUtils.setField(service, "activityRepository", repository);
    ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
    ActivityEntity activity = ActivityEntity.builder()
            .activityId(20001L).strategyId(100001L).status(1).build();
    when(eligibility.requireActive(20001L)).thenReturn(activity);
    when(repository.queryActivitySku(30001L)).thenReturn(activeSku());
    when(repository.purchaseSkuAndGrant(any(), anyLong())).thenReturn(order());

    service.createOrder(purchaseFactor());

    verify(eligibility).requireActive(20001L);
    verify(repository).purchaseSkuAndGrant(argThat(aggregate ->
            aggregate.getActivity() == activity), anyLong());
    verify(repository, never()).queryActivityById(anyLong());
}
```

Also update existing direct-construction tests to inject a mocked `ActivityEligibilityService` that returns their existing active `ActivityEntity`.

- [ ] **Step 2: Run the entry tests and verify RED**

Run:

```powershell
mvn -o -pl Big-Market-app -am '-Dtest=ActivityValidationEntryTest,ActivitySkuPurchaseTest,RaffleFailureCompensationTest,RaffleReplayTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: `ActivityValidationEntryTest` fails because the services do not yet have the canonical validator field and still query `IActivityRepository` directly.

- [ ] **Step 3: Replace duplicate validation with the canonical service**

In `ActivitySkuService`, inject `ActivityEligibilityService` and replace the activity query plus inline status/time checks with:

```java
ActivityEntity activity = activityEligibilityService.requireActive(activityId);
ActivitySkuEntity skuEntity = activityRepository.queryActivitySku(skuId);
```

In `RaffleService`, replace `IActivityRepository` with `ActivityEligibilityService` and use:

```java
ActivityEntity activity = activityEligibilityService.requireActive(factor.getActivityId());
if (activity.getStrategyId() == null) {
    throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
}
factor.setStrategyId(activity.getStrategyId());
```

In `ActivityAmory`, replace `IActivityRepository` with `ActivityEligibilityService` and make `assembleLotteryActivity` exactly:

```java
@Override
public void assembleLotteryActivity(Long activityId) {
    activityEligibilityService.requireActive(activityId);
}
```

- [ ] **Step 4: Delete the obsolete activity cache and responsibility chain**

Remove `cacheActivity` and `cacheGetActivity` from `IActivityRepository` and `ActivityRepository`. Remove the `IRedisService` field and import from `ActivityRepository`. Delete the four unused responsibility-chain files listed above.

Run:

```powershell
rg -n "cacheActivity|cacheGetActivity|ActivityChainHandler|ActivityInfoCheckHandler" Big-Market-domain/src/main Big-Market-infrastructure/src/main Big-Market-app/src/main
```

Expected: no matches.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run the Step 2 Maven command again.

Expected: all named tests pass.

- [ ] **Step 6: Commit Task 2**

```powershell
git add Big-Market-domain/src/main Big-Market-infrastructure/src/main Big-Market-app/src/test/java/shirlin/ai/test/Domain
git commit -m "refactor: unify activity validation entry points"
```

---

### Task 3: Resolve all strategy pools directly from MySQL

**Files:**
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/adapter/repository/IStrategyRepository.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/IStrategyArmory.java`
- Modify: `Big-Market-domain/src/main/java/shirlin/ai/domain/strategy/service/Armory/StrategyArmory.java`
- Modify: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/StrategyRepository.java`
- Modify: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/StrategyArmoryValidationTest.java`
- Rewrite: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/ExcludedPoolRefreshTest.java`
- Create: `Big-Market-app/src/test/java/shirlin/ai/test/Domain/StrategyArmoryMysqlSourceTest.java`

**Interfaces:**
- Consumes: existing MySQL query methods on `IStrategyRepository`.
- Produces: unchanged public draw methods on `IStrategyArmory`, now backed entirely by the current MySQL configuration.

- [ ] **Step 1: Write failing MySQL-source strategy tests**

Use deterministic pools so the test does not depend on random values:

```java
@Test
void nextDrawUsesChangedMysqlProbabilityWithoutAssembly() {
    IStrategyRepository repository = repositoryWithCommonRules();
    when(repository.queryStrategyById(1L)).thenReturn(strategy(100));
    when(repository.queryStrategyAwardListById(1L)).thenReturn(
            List.of(award(101, "1.00", 10), award(106, "0.00", -1)),
            List.of(award(102, "1.00", 10), award(106, "0.00", -1)));
    StrategyArmory armory = armory(repository);

    assertEquals(101, armory.getRandomAwardId(1L));
    assertEquals(102, armory.getRandomAwardId(1L));
}

@Test
void weightPoolComesFromCurrentMysqlRule() {
    IStrategyRepository repository = repositoryWithCommonRules();
    when(repository.queryStrategyById(1L)).thenReturn(strategy(100));
    when(repository.queryStrategyAwardListById(1L)).thenReturn(
            List.of(award(101, "0.50", 10), award(106, "0.50", -1)));
    when(repository.queryStrategyRuleByModel(1L, "rule_weight")).thenReturn(
            rule("{\"threshold_key\":\"draw_count\",\"groups\":[{" +
                    "\"threshold_value\":10,\"group_id\":\"vip\"," +
                    "\"award_rates\":{\"101\":1.00,\"106\":0.00}}]}"));

    assertEquals(101, armory(repository).getRandomAwardId(1L, "vip"));
}

@Test
void unknownWeightGroupIsRejected() {
    StrategyArmory armory = armory(validRepository());
    assertThrows(IllegalStateException.class,
            () -> armory.getRandomAwardId(1L, "removed-group"));
}

@Test
void excludingEveryAwardIsRejected() {
    StrategyArmory armory = armory(validRepository());
    assertThrows(IllegalArgumentException.class,
            () -> armory.getRandomAwardId(1L, Set.of(101, 106)));
}
```

Stub `rule_fallback` as `{"award_id":106}` and make award `106` unlimited (`awardCount=-1`, `awardSurplus=-1`). `strategy(100)` must return `totalProbability=1` and `probabilityPrecision=100`.

- [ ] **Step 2: Run strategy tests and verify RED**

Run:

```powershell
mvn -o -pl Big-Market-app -am '-Dtest=StrategyArmoryMysqlSourceTest,StrategyArmoryValidationTest,ExcludedPoolRefreshTest,ProbabilityTableBuilderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: the new test fails because `StrategyArmory` reads cached precision and range tables instead of rebuilding from MySQL.

- [ ] **Step 3: Introduce one immutable strategy definition loader**

Inside `StrategyArmory`, add a private record:

```java
private record StrategyDefinition(
        int precision,
        List<StrategyAwardEntity> awards,
        List<AwardRateRange> defaultTable,
        Map<String, List<AwardRateRange>> weightTables) {
}
```

Extract the existing validation in `assembleLotteryStrategy` into:

```java
private StrategyDefinition loadDefinition(Long strategyId)
```

The method must perform these operations using values read during this invocation:

1. Query the strategy and award list.
2. Require nonempty awards, unique positive award IDs, valid stock fields, positive precision, and `totalProbability == 1`.
3. Build the default table with `ProbabilityTableBuilder.build(defaultRates, precision, true)`.
4. Require a valid explicit fallback using `FallbackAwardPolicy.requireValid`.
5. Parse and validate lock and luck rules with `PostDrawRulePolicy.validate`.
6. Require the blacklist award, when configured, to equal the fallback award.
7. Parse every weight group, require `threshold_key=draw_count`, unique thresholds and group IDs, known award IDs, and build every weight table with `requireFull=true`.
8. Return immutable copies using `List.copyOf` and `Map.copyOf`.

Change assembly to validation only:

```java
@Override
public void assembleLotteryStrategy(Long strategyId) {
    loadDefinition(strategyId);
}
```

- [ ] **Step 4: Route every random selection through the same definition**

Implement the three methods as follows:

```java
@Override
public Integer getRandomAwardId(Long strategyId) {
    StrategyDefinition definition = loadDefinition(strategyId);
    return pick(definition.defaultTable());
}

@Override
public Integer getRandomAwardId(Long strategyId, String weightGroupId) {
    StrategyDefinition definition = loadDefinition(strategyId);
    List<AwardRateRange> table = definition.weightTables().get(weightGroupId);
    if (table == null || table.isEmpty()) {
        throw new IllegalStateException(
                "Configured weight pool is missing: " + weightGroupId);
    }
    return pick(table);
}

@Override
public Integer getRandomAwardId(Long strategyId, Set<Integer> excludedIds) {
    StrategyDefinition definition = loadDefinition(strategyId);
    if (excludedIds == null || excludedIds.isEmpty()) {
        return pick(definition.defaultTable());
    }
    Map<Integer, BigDecimal> remaining = definition.awards().stream()
            .filter(award -> !excludedIds.contains(award.getAwardId()))
            .collect(Collectors.toMap(
                    StrategyAwardEntity::getAwardId,
                    StrategyAwardEntity::getAwardRate));
    return pick(ProbabilityTableBuilder.build(
            remaining, definition.precision(), false));
}

private Integer pick(List<AwardRateRange> table) {
    int upperBound = table.get(table.size() - 1).getRangeEnd();
    return ProbabilityTableBuilder.pick(table, RANDOM.nextInt(upperBound));
}
```

The exclusion method must explicitly reject an empty `remaining` map before calling the builder so its error is deterministic.

- [ ] **Step 5: Remove strategy cache APIs from repository contracts**

Delete these methods from `IStrategyRepository` and `StrategyRepository`:

```java
storeStrategyAwardRangeTable
storeStrategyPrecision
getStrategyPrecision
getStrategyRangeTable
buildSubRangeTable
storeWeightRangeTable
getWeightRangeTable
```

Remove `IRedisService`, Redis key constants, and the Redis field from `StrategyRepository`. Update `IStrategyArmory` comments to describe MySQL-backed validation and selection.

- [ ] **Step 6: Update existing strategy tests**

Change `StrategyArmoryValidationTest` so a valid configuration asserts `assertDoesNotThrow(() -> armory.assembleLotteryStrategy(1L))`; keep invalid pool and invalid luck assertions but remove cache-publication verification. Rewrite `ExcludedPoolRefreshTest` to mock `IStrategyRepository` and invoke `StrategyArmory.getRandomAwardId(strategyId, excludedIds)` twice with sequential MySQL award lists.

- [ ] **Step 7: Run strategy tests and verify GREEN**

Run the Step 2 Maven command again.

Expected: all named tests pass and no Redis mock is required.

- [ ] **Step 8: Commit Task 3**

```powershell
git add Big-Market-domain/src/main/java/shirlin/ai/domain/strategy Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/adapter/repository/StrategyRepository.java Big-Market-app/src/test/java/shirlin/ai/test/Domain
git commit -m "refactor: load raffle strategy pools from mysql"
```

---

### Task 4: Remove the unused Redis runtime and dependencies

**Files:**
- Delete: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/redis/IRedisService.java`
- Delete: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/redis/RedissonService.java`
- Delete: `Big-Market-infrastructure/src/main/java/shirlin/ai/infrastructure/redis/package-info.java`
- Delete: `Big-Market-app/src/main/java/shirlin/ai/config/RedisClientConfig.java`
- Delete: `Big-Market-app/src/main/java/shirlin/ai/config/RedisClientConfigProperties.java`
- Modify: `Big-Market-app/src/main/resources/application-dev.yml`
- Modify: `Big-Market-app/pom.xml`
- Modify: `Big-Market-infrastructure/pom.xml`
- Modify: `pom.xml`

**Interfaces:**
- Consumes: the Redis-free repository contracts produced by Tasks 2 and 3.
- Produces: an application classpath and configuration that have no Redisson dependency or Redis connection requirement.

- [ ] **Step 1: Establish the failing structural check**

Run:

```powershell
rg -n "IRedisService|Redisson|redisson|redis.sdk.config|# Redis" Big-Market-app Big-Market-domain Big-Market-infrastructure pom.xml -g '*.java' -g '*.xml' -g '*.yml'
```

Expected: matches include the Redis service, client configuration, Maven dependencies, and YAML block.

- [ ] **Step 2: Delete the unused Redis implementation**

Delete the five Java/package files listed above. Before deletion, run `rg -n "IRedisService|RedissonService" . -g '*.java'` and confirm only the files being deleted and tests already rewritten by Task 3 remain.

- [ ] **Step 3: Remove Redis configuration and dependencies**

Remove the complete `redis.sdk.config` block from `application-dev.yml`. Remove:

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.44.0</version>
</dependency>
```

from root dependency management, remove `redisson-spring-boot-starter` from `Big-Market-app/pom.xml`, and remove the `redisson` dependency from `Big-Market-infrastructure/pom.xml`.

- [ ] **Step 4: Verify the structural check is GREEN**

Run the Step 1 command again.

Expected: exit code `1` with no matches.

- [ ] **Step 5: Compile all modules offline**

Run:

```powershell
mvn -o -DskipTests package
```

Expected: reactor build succeeds without Redisson on the dependency graph.

- [ ] **Step 6: Commit Task 4**

```powershell
git add pom.xml Big-Market-app/pom.xml Big-Market-infrastructure/pom.xml Big-Market-app/src/main Big-Market-app/src/main/resources/application-dev.yml Big-Market-infrastructure/src/main
git commit -m "refactor: remove redis configuration dependency"
```

---

### Task 5: Update operating documentation and run full regression

**Files:**
- Modify: `docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md`
- Modify: `docs/superpowers/plans/2026-10-06-mysql-authoritative-activity-validation.md` only to mark completed checkboxes during execution.

**Interfaces:**
- Consumes: the completed behavior from Tasks 1–4.
- Produces: deployment documentation that no longer instructs operators to refresh Redis or requires Redis for configuration.

- [ ] **Step 1: Update the migration and operation notes**

Replace the strategy update instruction with:

```markdown
- 活动、策略、奖品和规则配置以 MySQL 为唯一权威来源。抽奖请求按当前数据库配置构建概率区间，不需要刷新 Redis；修改策略时必须在一个受控数据库事务中完成整套配置，并在开放流量前运行策略装配校验。
```

Remove statements that say activity or strategy configuration is written to Redis. Preserve all inventory bucket, Outbox, MQ, refund, and delivery instructions.

- [ ] **Step 2: Run focused correctness tests**

Run:

```powershell
mvn -o -pl Big-Market-app -am '-Dtest=ActivityEligibilityServiceTest,ActivityValidationEntryTest,ActivitySkuPurchaseTest,RaffleFailureCompensationTest,RaffleReplayTest,StrategyArmoryMysqlSourceTest,StrategyArmoryValidationTest,ExcludedPoolRefreshTest,ProbabilityTableBuilderTest,FallbackAwardPolicyTest,PostDrawRulePolicyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: all named tests pass with zero failures and zero errors.

- [ ] **Step 3: Run the full test suite**

Run:

```powershell
mvn -o test
```

Expected: all non-conditional tests pass. Record the exact pass and skip counts; report any tests skipped because real MySQL or RabbitMQ environment variables are absent.

- [ ] **Step 4: Run the production package build**

Run:

```powershell
mvn -o -DskipTests package
```

Expected: all reactor modules report `SUCCESS`.

- [ ] **Step 5: Check repository invariants and diff quality**

Run:

```powershell
rg -n "cacheActivity|cacheGetActivity|ActivityChainHandler|IRedisService|Redisson|redisson|redis.sdk.config" Big-Market-app Big-Market-domain Big-Market-infrastructure pom.xml -g '*.java' -g '*.xml' -g '*.yml'
git diff --check
git status --short
```

Expected: the search has no matches, `git diff --check` has no output, and status contains only the plan checkbox updates plus any known pre-existing untracked artifacts.

- [ ] **Step 6: Commit documentation and plan completion state**

```powershell
git add docs/dev-ops/mysql/sql/2026-09-22-chain-readme.md docs/superpowers/plans/2026-10-06-mysql-authoritative-activity-validation.md
git commit -m "docs: describe mysql-authoritative raffle configuration"
```

- [ ] **Step 7: Review the final commit range**

Run:

```powershell
git log --oneline ac925c1..HEAD
git diff --stat ac925c1..HEAD
```

Confirm that the range contains only unified activity validation, MySQL strategy resolution, Redis removal, tests, and the corresponding documentation.
