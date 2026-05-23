package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.Rule.Chain.ActivityChainHandlerFactory;
import shirlin.ai.domain.strategy.service.IRaffleService;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

/**
 * 抽奖服务编排
 *
 * Phase 1 — 活动校验责任链 ActivityChain（ActivityInfoCheck → ActivitySkuStock）
 * Phase 2 — 事务：创建参与订单 + 扣减总/月/日额度
 * Phase 3 — 执行抽奖（策略链 + 规则树）
 * Phase 4 — 回填订单结果
 */
@Slf4j
@Service
public class RaffleService implements IRaffleService {

    @Resource
    private ActivityChainHandlerFactory activityChainHandlerFactory;

    @Resource
    private IStrategyRepository strategyRepository;

    @Resource
    private IActivityRepository activityRepository;

    @Resource
    private IRaffleStrategy raffleStrategy;

    /** 自注入以确保 @Transactional 通过 Spring AOP 代理生效，避免 this. 调用绕过代理 */
    @Resource
    private RaffleService self;

    @Override
    public RaffleResultEntity doRaffle(String userId, Long strategyId) {

        // Phase 1：活动校验责任链
        activityChainHandlerFactory.getChainHead().apply(userId, strategyId);

        // Phase 2：事务内操作（创建订单 + 扣减三层额度）
        // 通过 self 调用，确保 @Transactional 注解由 Spring 代理拦截，失败时正确回滚
        Long orderId = self.createOrderAndDeductQuota(userId, strategyId);

        // Phase 3：执行抽奖（策略链 + 规则树）
        RaffleFactorEntity factor = RaffleFactorEntity.builder()
                .userId(userId)
                .strategyId(strategyId)
                .build();
        RaffleResultEntity result = raffleStrategy.performRaffle(factor);

        // Phase 4：回填订单
        activityRepository.updateUserRaffleOrder(orderId, result.getAwardId(), result.getAwardType());

        log.info("抽奖完成 userId:{} strategyId:{} awardId:{}", userId, strategyId, result.getAwardId());
        return result;
    }

    /**
     * Phase 2 事务：创建订单 + 扣减三层额度
     * 任意一步失败均回滚订单插入，避免占用库存却未写入记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrderAndDeductQuota(String userId, Long strategyId) {

        Long orderId = activityRepository.createUserRaffleOrder(userId, strategyId);

        if (!activityRepository.deductUserTotalQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserMonthlyQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserDailyQuota(userId, strategyId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        return orderId;
    }
}
