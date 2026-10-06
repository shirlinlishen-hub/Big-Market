package shirlin.ai.domain.Activity.service;

import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.time.Clock;
import java.util.Date;

@Service
public class ActivityEligibilityService {

    private final IActivityRepository activityRepository;
    private final Clock clock;

    public ActivityEligibilityService(IActivityRepository activityRepository, Clock clock) {
        this.activityRepository = activityRepository;
        this.clock = clock;
    }

    public ActivityEntity requireActive(Long activityId) {
        if (activityId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }

        ActivityEntity activity = activityRepository.queryActivityById(activityId);
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
        }

        Date now = Date.from(clock.instant());
        if (activity.getBeginTime() != null && now.before(activity.getBeginTime())) {
            throw new AppException(ResponseCode.STRATEGY_NOT_ACTIVE.getCode());
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            throw new AppException(ResponseCode.ACTIVITY_EXPIRED.getCode());
        }
        return activity;
    }
}
