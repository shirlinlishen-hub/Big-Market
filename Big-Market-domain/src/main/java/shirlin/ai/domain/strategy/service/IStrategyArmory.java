package shirlin.ai.domain.strategy.service;

import java.util.Set;

/** Validates MySQL strategy configuration and selects awards from the current definition. */
public interface IStrategyArmory {

    /** Fail fast when the current MySQL strategy configuration is invalid. */
    void assembleLotteryStrategy(Long StrategyId);

    /**
     * 获取商品Id
     * @param StrategyId
     * @return
     */
    Integer getRandomAwardId(Long StrategyId);


    Integer getRandomAwardId(Long strategyId, Set<Integer> excludeAwardIds);

    Integer getRandomAwardId(Long strategyId, String weightGroupId);

}
