package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRafflleStrategy {

    RaffleResultEntity performRaffle(RaffleFactorEntity factor);

}
