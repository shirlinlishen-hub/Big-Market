package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

/**
 * N次解锁规则（规则树 Lock 节点）
 * 检查用户累计抽奖次数是否满足解锁条件；结果由规则树内联使用，不作为责任链节点调用
 */
@Slf4j
@Service("ruleLockFilterNode")
public class RuleLockFilterNode extends AbstractRuleFilterService<RaffleFactorEntity, DefaultLogicFactory.DynamicContext,RuleFilterResultEntity> {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity factory, DefaultLogicFactory.DynamicContext dynamicContext) throws Exception {
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULELOCK.getRuleModel());

        if (rule == null) {
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.ALLOW)
                    .build();
        }

        RuleLockConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleLockConfigEntity.class);
        int usedCount = strategyRepository.queryUserDrawCount(factory.getUserId(), factory.getStrategyId());

        boolean isLocked = config.getLockedAwardIds() != null
                && dynamicContext.getAwardId() != null
                && config.getLockedAwardIds().contains(dynamicContext.getAwardId())
                && usedCount < config.getUnlockCount();

        if (isLocked) {
            log.info("Lock 未解锁 userId:{} awardId:{} usedCount:{} unlockCount:{}",
                    factory.getUserId(), dynamicContext.getAwardId(), usedCount, config.getUnlockCount());
            Integer fallbackAwardId = strategyRepository.queryMaxAwardId(factory.getStrategyId());
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.TAKE_OVER)
                    .awardId(fallbackAwardId)
                    .build();
        }

        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }


}
