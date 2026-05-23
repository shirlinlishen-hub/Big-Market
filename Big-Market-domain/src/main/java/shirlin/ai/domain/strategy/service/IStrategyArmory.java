package shirlin.ai.domain.strategy.service.Armory;

import java.util.Set;

/**
 * 进行抽奖活动的预热
 * 将活动的配置，商品以及概率等信息
 * 存放进Redis中
 */
public interface IStrategyArmory {

    void assembleLotteryStrategy(Long StrategyId);
    Integer getRandomAwardId(Long StrategyId);
    Integer getRandomAwardId(Long strategyId, Set<Integer> excludeAwardIds);

}
