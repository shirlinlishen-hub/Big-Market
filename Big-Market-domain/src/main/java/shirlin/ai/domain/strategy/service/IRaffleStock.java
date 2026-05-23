package shirlin.ai.domain.strategy.service;

/**
 * 处理完扣减库存结束后，写入到redia队列中的库存消耗数据，由trigger的定时任务定时扫描获取redis队列数据，从而更新库表数据
 * 异步更新db库存数据
 */
public interface IRaffleStock {




}
