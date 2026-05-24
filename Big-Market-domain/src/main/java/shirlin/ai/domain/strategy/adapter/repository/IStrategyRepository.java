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

    // ---- 规则配置查询（统一入口，ruleModel 对应 RuleTypeVO.ruleModel） ----

    StrategyRuleEntity queryStrategyRuleByModel(Long strategyId, String ruleModel);

    // ---- Redis 缓存：概率区间 / 精度 / 兜底奖品 ----

    void storeStrategyAwardRangeTable(Long strategyId, List<AwardRateRange> rangeTable);

    void storeStrategyPrecision(Long strategyId, int precision);

    void storeStrategyMaxAward(Long strategyId, StrategyAwardEntity maxAward);

    int getStrategyPrecision(Long strategyId);

    List<AwardRateRange> getStrategyRangeTable(Long strategyId);

    StrategyAwardEntity getStrategyMaxAward(Long strategyId);

    List<AwardRateRange> getOrBuildSubRangeTable(Long strategyId, Set<Integer> excludeAwardIds, String cacheKey);

    void storeWeightRangeTable(Long strategyId, String groupId, List<AwardRateRange> table);

    List<AwardRateRange> getWeightRangeTable(Long strategyId, String groupId);

    // ---- 奖品库存操作（规则树 Stock 节点，Redis decr 原子扣减） ----

    /** 预扣奖品库存；返回 true 表示扣减成功（有剩余库存），false 表示库存耗尽 */
    boolean deductStock(Long strategyId, Integer awardId);

    /** 装配时初始化奖品库存到 Redis（awardSurplus 为 null 则跳过，视为无限库存） */
    void cacheStrategyAwardStock(Long strategyId, Integer awardId, Integer awardSurplus);

    /** 从 Redis 兜底缓存中获取最大概率奖品的 awardId */
    Integer queryMaxAwardId(Long strategyId);

    // ---- 用户状态查询 ----

    /** 查询用户在本策略下的累计抽奖次数 */
    int queryUserDrawCount(String userId, Long strategyId);

    /** 查询用户权重值（当前实现 = 累计抽奖次数，可按需扩展） */
    int queryUserThresholdValue(String userId, Long strategyId);

    /** 查询用户运气值 */
    int queryUserLuckValue(String userId, Long strategyId);

    /** 运气值 +1 */
    void incrementLuckValue(String userId, Long strategyId);

    /** 重置运气值为 0 */
    void resetUserLuckValue(String userId, Long strategyId);

}
