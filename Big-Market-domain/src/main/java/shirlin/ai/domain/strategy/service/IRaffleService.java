package shirlin.ai.domain.strategy.service;

import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;

public interface IRaffleService {

    RaffleResultEntity doRaffle(String userId, Long strategyId);

    Long createOrderAndDeductQuota(String userId, Long strategyId);

}
