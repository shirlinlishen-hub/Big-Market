package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleStrategy {

    RaffleResultEntity performRaffle(RaffleFactorEntity factor) throws Exception;

}
