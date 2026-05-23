package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.domain.strategy.service.IStrategyArmory;

import java.util.Collections;
import java.util.Set;

public abstract class AbstrackRaffleStrategy implements IRaffleStrategy {

    @Resource
    private IStrategyRepository strategyRepository;

    @Resource
    private IStrategyArmory armory;

    @Override
    public RaffleResultEntity performRaffle(RaffleFactorEntity factor) {

        Long strategyId = factor.getStrategyId();

        // 1. 前置规则过滤（责任链：黑名单 → 权重 → 默认）
        RuleFilterResultEntity beforeResult = doBeforeRaffleRuleFilter(factor);

        // 黑名单命中，直接返回兜底奖品，不进入抽奖
        if (beforeResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(beforeResult.getType())) {
            return buildResult(strategyId, beforeResult.getAwardId());
        }

        // 2. 执行抽奖：概率区间二分查找
        Set<Integer> excludeAwardIds = (beforeResult != null && beforeResult.getExcludeAwardIds() != null)
                ? beforeResult.getExcludeAwardIds()
                : Collections.emptySet();
        Integer awardId = armory.getRandomAwardId(strategyId, excludeAwardIds);

        // 3. 后置规则过滤（规则树：Lock → Stock → 兜底）
        RuleFilterResultEntity afterResult = doAfterRaffleRuleFilter(factor, awardId);
        if (afterResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(afterResult.getType())) {
            return buildResult(strategyId, afterResult.getAwardId());
        }

        return buildResult(strategyId, awardId);
    }

    /**
     * 前置规则（责任链）：子类实现，决定用哪个奖池
     */
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) {
        return null;
    }

    /**
     * 后置规则（规则树）：子类实现，决定最终给什么奖品
     */
    protected RuleFilterResultEntity doAfterRaffleRuleFilter(RaffleFactorEntity factor, Integer awardId) {
        return null;
    }

    private RaffleResultEntity buildResult(Long strategyId, Integer awardId) {
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
