package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.Activity.service.Rule.Chain.ActivityChainHandlerFactory;
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
    private IActivityRepository activityRepository;

    @Resource
    private IRaffleStrategy raffleStrategy;

    @Resource
    private RaffleOrderService raffleOrderService;

    @Override
    public RaffleResultEntity doRaffle(ActivityFactorEntity factor) {


        // 1：活动校验责任链
        activityChainHandlerFactory.getChainHead().apply(factor);

        // 2：事务内操作（创建订单 + 扣减三层额度）
        Long orderId = raffleOrderService.createOrderAndDeductQuota(factor);

        // 3：执行抽奖（策略链 + 规则树）
        RaffleFactorEntity raffleFactorEntity = RaffleFactorEntity.builder()
                .userId(factor.getUserId())
                .strategyId(factor.getStrategyId())
                .build();
        RaffleResultEntity result;
        try {
            result = raffleStrategy.performRaffle(raffleFactorEntity);
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "抽奖执行失败: " + e.getMessage(), e);
        }

        // 4：回填订单
        activityRepository.updateUserRaffleOrder(orderId, result.getAwardId(), result.getAwardType());

        log.info("抽奖完成 userId:{} strategyId:{} awardId:{}", factor.getUserId(), factor.getStrategyId(), result.getAwardId());
        return result;
    }

}
