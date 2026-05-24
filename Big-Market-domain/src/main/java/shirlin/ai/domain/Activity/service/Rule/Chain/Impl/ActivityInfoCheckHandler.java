package shirlin.ai.domain.Activity.service.Rule.Chain.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.Activity.service.Rule.Chain.AbstractActivityChainHandler;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.util.Date;

/**
 * 活动校验链 Node1 — 基本信息校验
 * 检查：策略是否存在 + 在有效期内 + 活动状态正常 + 用户未被活动黑名单拉黑
 */
@Slf4j
@Service("activityInfoCheckHandler")
public class ActivityInfoCheckHandler extends AbstractActivityChainHandler {

    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean apply(ActivityFactorEntity factor) {

        Long activityId = factor.getActivityId();


        // 1. 活动是否存在且状态正常
        ActivityEntity activity = activityRepository.cacheGetActivity(activityId);
        if (activity == null) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }

        if (activity.getStatus() == null || activity.getStatus() != 1) {
            log.warn("策略状态异常 activityId:{} status:{}", activityId, activity.getStatus());
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }

        // 2. 活动时间校验
        Date now = new Date();
        //活动尚未开始
        if (activity.getBeginTime() != null && now.before(activity.getBeginTime())) {
            log.warn("活动未开始 strategyId:{}", activityId);
            throw new AppException(ResponseCode.STRATEGY_NOT_ACTIVE.getInfo());
        }
        //活动已经结束
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            log.warn("活动已结束 activityId:{}", activityId);
            throw new AppException(ResponseCode.ACTIVITY_EXPIRED.getInfo());
        }

        // 通过，继续下一个节点
        return next(factor);
    }

}
