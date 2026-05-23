package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.service.IRafflleService;
import shirlin.ai.domain.strategy.service.IStrategyArmory;

import java.util.Set;

public class AbstrackRaffleService implements IRafflleService {

    @Resource
    private IStrategyRepository  strategyRepository;

    @Resource
    private IStrategyArmory armory;

    @Override
    public RaffleResultEntity performRaffle(RaffleFactorEntity factor) {

        Long strategyId = factor.getStrategyId();
        String userId = factor.getUserId();

        // 1. 前置规则过滤
        RuleFilterResultEntity beforeResult = this.doBeforeRaffleRuleFilter(factor);

        // 2. 如果前置规则直接接管了结果(比如运气值兜底直接命中), 则直接返回
        if (beforeResult != null && RuleFilterResultEntity.Type.TAKE_OVER.equals(beforeResult.getType())) {
            return buildResult(beforeResult.getAwardId());
        }

        // 3. 执行抽奖 — 概率区间二分查找
        Set<Integer> excludeAwardIds = beforeResult.getExcludeAwardIds();
        Integer awardId = armory.getRandomAwardId(strategyId, excludeAwardIds);

        // 4. 后置规则过滤 (运气值累加、库存校验等)
        RuleFilterResultEntity afterResult = this.doAfterRaffleRuleFilter(factor, awardId);
        if (RuleFilterResultEntity.Type.TAKE_OVER.equals(afterResult.getType())) {
            return buildResult(afterResult.getAwardId());
        }

        return buildResult(awardId);
    }

    /**
     * 前置规则: 子类实现, 决定奖池组成
     */
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) {
        return null;
    }

    /**
     * 后置规则: 子类实现, 抽完后的校验和副作用
     */
    protected RuleFilterResultEntity doAfterRaffleRuleFilter(RaffleFactorEntity factor, Integer awardId) {
        return null;
    }


    private RaffleResultEntity buildResult(Integer awardId) {
        StrategyAwardEntity award = strategyRepository.queryStrategyAward(awardId);
        return RaffleResultEntity.builder()
                .awardId(awardId)
                .awardType(award.getAwardType())
                .sort(award.getSort())
                .build();
    }
}
