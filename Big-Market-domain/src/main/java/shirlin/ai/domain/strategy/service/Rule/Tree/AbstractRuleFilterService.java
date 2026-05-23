package shirlin.ai.domain.strategy.service.Rule;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

public class AbstractRuleFilterService implements IStrategyLogicFilterService {


    @Override
    public RuleFilterResultEntity filter(RaffleFactorEntity factory) {
        //参数校验
        Long strategyId = factory.getStrategyId();
        String userId = factory.getUserId();
        if(strategyId == null || userId == null)
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getInfo());
        return  doFilter(factory);

    }

    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {
        return null;
    }
}
