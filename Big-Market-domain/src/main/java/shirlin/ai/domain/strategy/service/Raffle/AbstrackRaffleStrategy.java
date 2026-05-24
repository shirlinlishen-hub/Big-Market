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
    private IStrategyRepository strategyRepository;

    @Resource
    private IStrategyArmory armory;

    @Resource
    @Qualifier("strategyPreRuleFilter")
    private BusinessLinkedList<RaffleFactorEntity,
            StrategyPreRuleFilterFactory.DynamicContext,
            RuleFilterResultEntity> preRuleChain;

    @Override
    public RaffleResultEntity performRaffle(RaffleFactorEntity factor) throws Exception {

        Long strategyId = factor.getStrategyId();

        // 1. 前置规则过滤（责任链：黑名单 → 权重 → 默认）
        RuleFilterResultEntity beforeResult = doBeforeRaffleRuleFilter(factor);

        // 黑名单命中，直接返回兜底奖品，不进入抽奖
        if (beforeResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(beforeResult.getType())) {
            return buildResult(strategyId, beforeResult.getAwardId());
        }

        // 2. 执行抽奖：权重命中走专属区间表，否则走默认（含排除）
        Integer awardId;
        String weightGroupId = (beforeResult != null) ? beforeResult.getWeightGroupId() : null;
        if (weightGroupId != null) {
            awardId = armory.getRandomAwardId(strategyId, weightGroupId);
        } else {
            Set<Integer> excludeAwardIds = (beforeResult != null && beforeResult.getExcludeAwardIds() != null)
                    ? beforeResult.getExcludeAwardIds()
                    : Collections.emptySet();
            awardId = armory.getRandomAwardId(strategyId, excludeAwardIds);
        }

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
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) throws Exception {

        StrategyPreRuleFilterFactory.DynamicContext ctx =
                new StrategyPreRuleFilterFactory.DynamicContext();
        RuleFilterResultEntity result = preRuleChain.apply(factor, ctx);
        if (result != null) return result;  // TAKE_OVER（黑名单命中）
        // ALLOW：把权重分组ID和排除集回填进 result
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .weightGroupId(ctx.getWeightGroupId())
                .excludeAwardIds(ctx.getExcludeAwardIds())
                .build();
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
