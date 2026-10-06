package shirlin.ai.domain.strategy.adapter.repository;

import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;

import java.util.List;

public interface IStrategyRepository {

    // ---- Strategy / Award 基础查询 ----

    List<StrategyAwardEntity> queryStrategyAwardListById(Long strategyId);

    StrategyAwardEntity queryStrategyAward(Long strategyId, Integer awardId);

    StrategyEntity queryStrategyById(Long strategyId);

    // ---- 规则配置查询（统一入口，ruleModel 对应 RuleTypeVO.ruleModel） ----

    StrategyRuleEntity queryStrategyRuleByModel(Long strategyId, String ruleModel);

    /** Resolve and validate the explicit unlimited-stock fallback award. */
    Integer queryFallbackAwardId(Long strategyId);

    // ---- 用户状态查询 ----

    /** 查询用户在本策略下的累计抽奖次数 */
    int queryUserDrawCount(String userId, Long strategyId);

    /** 查询用户权重值（当前实现 = 累计抽奖次数，可按需扩展） */
    int queryUserThresholdValue(String userId, Long strategyId);

}
