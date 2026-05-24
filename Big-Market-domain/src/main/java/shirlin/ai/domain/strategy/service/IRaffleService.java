package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleService {

    RaffleResultEntity doRaffle(ActivityFactorEntity factor);

}
