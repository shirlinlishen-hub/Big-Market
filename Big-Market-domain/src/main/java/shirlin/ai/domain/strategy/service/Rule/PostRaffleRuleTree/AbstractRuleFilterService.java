package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree;

import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory.DefaultLogicFactory;
import shirlin.ai.types.design.tree.AbstractMultiThreadStrategyRouter;
import shirlin.ai.types.design.tree.StrategyHandler;

public class AbstractRuleFilterService<RaffleFactorEntity,DynamicContext,RuleFilterResultEntity> extends AbstractMultiThreadStrategyRouter<RaffleFactorEntity, DefaultLogicFactory.DynamicContext,RuleFilterResultEntity> {


    @Override
    protected void multiThread(RaffleFactorEntity var1, DefaultLogicFactory.DynamicContext var2) throws InterruptedException {

    }

    @Override
    protected RuleFilterResultEntity doApply(RaffleFactorEntity var1, DefaultLogicFactory.DynamicContext var2) throws Exception {
        return null;
    }

    @Override
    public StrategyHandler<RaffleFactorEntity, DefaultLogicFactory.DynamicContext, RuleFilterResultEntity> get(RaffleFactorEntity var1, DefaultLogicFactory.DynamicContext var2) throws Exception {
        return null;
    }
}
