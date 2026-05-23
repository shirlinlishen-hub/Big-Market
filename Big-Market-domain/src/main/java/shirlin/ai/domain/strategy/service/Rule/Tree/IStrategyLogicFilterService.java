package shirlin.ai.domain.strategy.service.Rule;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;

public interface IStrategyLogicFilterService {

    RuleFilterResultEntity filter(RaffleFactorEntity factory);

}
