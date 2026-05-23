package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.Factory.DefaultLogicFactory;
import shirlin.ai.domain.strategy.service.Rule.IStrategyLogicFilterService;

import java.util.Set;

public class DefaultRaffleService extends AbstrackRaffleService {


    @Resource
    private DefaultLogicFactory  defaultLogicFactory;

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    protected RuleFilterResultEntity doBeforeRaffleRuleFilter(RaffleFactorEntity factor) {

        //1. N次解锁
        IStrategyLogicFilterService ruleModel = defaultLogicFactory.getFilter(RuleTypeVO.getRuleBeanName(2));
        RuleFilterResultEntity lockResult = ruleModel.filter(factor);

        Set<Integer> excludeIds = lockResult.getExcludeAwardIds();

        //2. 运气值兜底检查
        IStrategyLogicFilterService ruleModel2 = defaultLogicFactory.getFilter(RuleTypeVO.getRuleBeanName(3));
        RuleFilterResultEntity luckResult = ruleModel2.filter(factor);
        if(RuleFilterResultEntity.Type.TAKE_OVER.equals(luckResult.getType()))
            return luckResult;

        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .excludeAwardIds(excludeIds)
                .build();
    }

    @Override
    protected RuleFilterResultEntity doAfterRaffleRuleFilter(RaffleFactorEntity factor, Integer awardId) {
        //1. 库存扣减 （Redis预扣 + DB异步同步）
        boolean stockResult = strategyRepository.deductStock(factor.getStrategyId(),awardId);
        if(!stockResult){
            int awardId_fallback = strategyRepository.queryMaxAwardId(factor.getStrategyId());
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.TAKE_OVER)
                    .awardId(awardId_fallback) // 兜底奖品ID, 实际应从规则配置读取
                    .build();
        }

        // 2. 运气值累加 (未中大奖时+1)
        if (!isBigAward(awardId)) {
            strategyRepository.incrementLuckValue(factor.getUserId(), factor.getStrategyId());
        }

        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }

    private boolean isBigAward(Integer awardId) {
        // 一等奖、二等奖视为大奖
        return awardId == 101 || awardId == 102;
    }
}
