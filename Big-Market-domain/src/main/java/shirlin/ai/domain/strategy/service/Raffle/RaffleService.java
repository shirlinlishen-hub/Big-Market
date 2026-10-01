package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.service.IRaffleService;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

/**
 * 抽奖服务编排
 *
 * Reserve quota, run the strategy, then persist the award and delivery task.
 */
@Slf4j
@Service
public class RaffleService implements IRaffleService {

    @Resource
    private IRaffleStrategy raffleStrategy;

    @Resource
    private RaffleOrderService raffleOrderService;
    @Resource
    private IActivityRepository activityRepository;

    @Override
    public RaffleResultEntity doRaffle(ActivityFactorEntity factor) {
        if (factor == null || factor.getActivityId() == null || factor.getUserId() == null
                || factor.getUserId().isBlank() || factor.getOutBusinessNo() == null
                || factor.getOutBusinessNo().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }
        ActivityEntity activity = activityRepository.queryActivityById(factor.getActivityId());
        if (activity == null || activity.getStrategyId() == null || activity.getStatus() == null
                || activity.getStatus() != 1) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
        }
        factor.setStrategyId(activity.getStrategyId());
        DrawOrderEntity order = raffleOrderService.reserveDraw(factor);
        if (order.getStatus() == 1) {
            return RaffleResultEntity.builder()
                    .awardId(order.getAwardId()).awardType(order.getAwardType()).build();
        }
        if (!order.isNewlyCreated()) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "抽奖正在处理中");
        }

        // The post-draw rules and stock reservation execute in completeDraw's transaction.
        RaffleFactorEntity raffleFactorEntity = RaffleFactorEntity.builder()
                .userId(factor.getUserId())
                .strategyId(factor.getStrategyId())
                .build();
        RaffleResultEntity result;
        try {
            result = raffleStrategy.performRaffle(raffleFactorEntity);
            result = raffleOrderService.completeDraw(order, result);
        } catch (Exception e) {
            try {
                raffleOrderService.cancelFailedDraw(order);
            } catch (Exception compensationError) {
                e.addSuppressed(compensationError);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "抽奖执行失败: " + e.getMessage(), e);
        }

        log.info("抽奖完成 userId:{} strategyId:{} awardId:{}", factor.getUserId(), factor.getStrategyId(), result.getAwardId());
        return result;
    }

}
