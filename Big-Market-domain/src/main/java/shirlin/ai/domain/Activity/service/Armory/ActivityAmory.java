package shirlin.ai.domain.Activity.service.Armory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.service.IActivityArmory;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ActivityAmory implements IActivityArmory {

    @Resource
    private IActivityRepository activityRepository;

    /**
     * 活动配置信息装配
     * 包括SKU的库存
     * @param activityId
     */
    @Override
    public void assembleLotteryActivity(Long activityId) {

        //1. 获取当前Activity的配置信息以及全部SKU信息
        ActivityEntity activity = activityRepository.queryActivityById(activityId);
        List<ActivitySkuEntity> skuEntityList = activityRepository.querySkuByActivityId(activityId);

        //2. 缓存活动配置信息以及Sku的库存信息
        activityRepository.cacheActivity(activity);
        for(ActivitySkuEntity skuEntity : skuEntityList){
            activityRepository.cacheActivitySkuStock(activityId, skuEntity.getSkuId(), skuEntity.getStockCount());
        }

    }
}
