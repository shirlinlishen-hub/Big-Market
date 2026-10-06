package shirlin.ai.domain.Activity.service.Armory;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.service.ActivityEligibilityService;
import shirlin.ai.domain.Activity.service.IActivityArmory;

@Service
public class ActivityAmory implements IActivityArmory {

    @Resource
    private ActivityEligibilityService activityEligibilityService;

    /**
     * 活动配置信息装配
     * 使用统一活动校验入口验证MySQL中的当前配置。
     * @param activityId
     */
    @Override
    public void assembleLotteryActivity(Long activityId) {
        activityEligibilityService.requireActive(activityId);
    }
}
