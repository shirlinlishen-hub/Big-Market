package shirlin.ai.domain.strategy.service.Rule.Tree;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

public class AbstractRuleFilterService implements IStrategyLogicFilterService {


    @Override
    public RuleFilterResultEntity filter(RaffleFactorEntity factory) {
        

    }

    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {
        return null;
    }
}
