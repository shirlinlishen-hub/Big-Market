package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

/**
 * 抽奖参与订单服务
 * 职责：在事务内创建参与订单并原子扣减三层额度（总量 / 月 / 日）
 * 从 RaffleService 拆出，避免自注入循环依赖。
 */
@Slf4j
@Service
public class RaffleOrderService {

    @Resource
    private IActivityRepository activityRepository;

    @Transactional(rollbackFor = Exception.class)
    public Long createOrderAndDeductQuota(ActivityFactorEntity factor) {

        String userId   = factor.getUserId();
        Long strategyId = factor.getStrategyId();
        Long activityId = factor.getActivityId();

        Long orderId = activityRepository.createUserRaffleOrder(userId, strategyId);

        if (!activityRepository.deductUserTotalQuota(userId, activityId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserMonthlyQuota(userId, activityId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        if (!activityRepository.deductUserDailyQuota(userId, activityId)) {
            throw new AppException(ResponseCode.DRAW_COUNT_NOT_ENOUGH.getInfo());
        }

        return orderId;
    }
}
