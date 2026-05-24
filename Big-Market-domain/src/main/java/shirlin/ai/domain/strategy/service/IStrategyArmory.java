package shirlin.ai.domain.strategy.service;

import java.util.Set;

/**
 * 进行抽奖活动的预热
 * 将活动的配置，商品以及概率等信息
 * 存放进Redis中
 */
public interface IStrategyArmory {

    /**
     * 活动信息预热，包括配置，库存等
     * @param StrategyId
     */
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
