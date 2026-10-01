package shirlin.ai.domain.Activity.service.Sku;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.aggregate.CreateSkuOrderAggregate;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.service.IActivitySkuService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

@Service
public class ActivitySkuService implements IActivitySkuService {


    @Resource
    private IActivityRepository  activityRepository;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ActivityOrderEntity createOrder(ActivityFactorEntity factor) {

        if (factor == null || factor.getActivityId() == null || factor.getSkuId() == null
                || factor.getUserId() == null || factor.getUserId().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }

        Long activityId = factor.getActivityId();
        Long skuId = factor.getSkuId();
        String userId = factor.getUserId();

        //1. 查询SKU和活动信息
        ActivityEntity activity = activityRepository.queryActivityById(factor.getActivityId());
        ActivitySkuEntity skuEntity = activityRepository.queryActivitySku(factor.getSkuId());

        //2. 活动状态校验
        if(activity == null || activity.getStatus() == null || activity.getStatus() != 1){
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getCode());
        }
        java.util.Date now = new java.util.Date();
        if ((activity.getBeginTime() != null && now.before(activity.getBeginTime()))
                || (activity.getEndTime() != null && now.after(activity.getEndTime()))) {
            throw new AppException(ResponseCode.ACTIVITY_EXPIRED.getCode());
        }

        if (skuEntity == null || !activityId.equals(skuEntity.getActivityId())
                || skuEntity.getStatus() == null || skuEntity.getStatus() != 1
                || factor.getOutBusinessNo() == null || factor.getOutBusinessNo().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }

        CreateSkuOrderAggregate aggregate = CreateSkuOrderAggregate.builder()
                .sku(skuEntity)
                .activity(activity)
                .factor(factor)
                .build();
        return activityRepository.purchaseSkuAndGrant(aggregate, skuEntity.getActivityCountId());
    }


}
