package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.AbstractRuleFilterService;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;

@Slf4j
@Service("ruleAwardStockFilterNode")
public class RuleAwardStockFilterNode extends AbstractRuleFilterService<RaffleFactorEntity, DefaultLogicFactory.DynamicContext, RuleFilterResultEntity> {

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity factory, DefaultLogicFactory.DynamicContext dynamicContext) throws Exception {
        // Stock 扣减逻辑已内联至 DefaultRaffleStrategy.doAfterRaffleRuleFilter；此节点预留，暂不启用
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }
}
