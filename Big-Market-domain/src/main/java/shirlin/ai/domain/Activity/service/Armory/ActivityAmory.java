package shirlin.ai.domain.Activity.service.Armory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.service.IActivityArmory;

@Slf4j
@Service
public class ActivityAmory implements IActivityArmory {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 活动配置信息装配
     * Redis只缓存活动只读配置；SKU库存以MySQL库存桶为权威数据源。
     * @param activityId
     */
    @Override
    public void assembleLotteryActivity(Long activityId) {

        //1. 获取当前Activity的配置信息
        ActivityEntity activity = activityRepository.queryActivityById(activityId);
        //2. 只缓存活动配置
        activityRepository.cacheActivity(activity);

    }
}
