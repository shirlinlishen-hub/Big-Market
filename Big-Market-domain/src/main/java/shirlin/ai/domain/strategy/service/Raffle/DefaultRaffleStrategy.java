package shirlin.ai.domain.strategy.service.Raffle;

import org.springframework.stereotype.Service;

/**
 * 默认抽奖策略
 *
 * 前置（责任链）：黑名单 → 权重 → 默认
 * Post-draw rules and stock are resolved in the result transaction.
 */
@Service
public class DefaultRaffleStrategy extends AbstrackRaffleStrategy {
}
