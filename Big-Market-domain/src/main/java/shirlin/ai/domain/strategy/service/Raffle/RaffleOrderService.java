package shirlin.ai.domain.strategy.service.Raffle;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.service.ActivityEligibilityService;
import shirlin.ai.domain.strategy.adapter.repository.IRaffleOrderRepository;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.IRaffleOrderService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

/**
 * 抽奖参与订单服务
 * 职责：在事务内创建参与订单并原子扣减三层额度（总量 / 月 / 日）
 * 从 RaffleService 拆出，避免自注入循环依赖。
 */
@Slf4j
@Service
public class RaffleOrderService implements IRaffleOrderService {

    @Resource
    private IRaffleOrderRepository raffleOrderRepository;

    @Resource
    private ActivityEligibilityService activityEligibilityService;

    @Transactional(rollbackFor = Exception.class)
    public DrawOrderEntity reserveDraw(ActivityFactorEntity factor) {
        if (factor == null || factor.getUserId() == null || factor.getActivityId() == null
                || factor.getStrategyId() == null || factor.getOutBusinessNo() == null
                || factor.getOutBusinessNo().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }
        DrawOrderEntity existing = raffleOrderRepository.findByRequest(
                factor.getUserId(), factor.getOutBusinessNo());
        if (existing != null) {
            if (!factor.getActivityId().equals(existing.getActivityId())
                    || !factor.getStrategyId().equals(existing.getStrategyId())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
            }
            return existing;
        }
        ActivityEntity activity = activityEligibilityService.requireActive(factor.getActivityId());
        if (!factor.getStrategyId().equals(activity.getStrategyId())) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
        }
        return raffleOrderRepository.reserveDraw(factor);
    }

    @Override
    public RaffleResultEntity completeDraw(DrawOrderEntity order, RaffleResultEntity candidate) {
        return raffleOrderRepository.completeDraw(order, candidate);
    }

    @Override
    public void cancelFailedDraw(DrawOrderEntity order) {
        raffleOrderRepository.cancelFailedDraw(order);
    }
}
