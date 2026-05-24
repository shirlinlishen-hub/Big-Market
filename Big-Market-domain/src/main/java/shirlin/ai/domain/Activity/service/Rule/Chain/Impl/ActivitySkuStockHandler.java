package shirlin.ai.domain.Activity.service.Rule.Chain.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.service.Rule.Chain.AbstractActivityChainHandler;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

/**
 * 活动校验链 Node2 — SKU 库存校验
 * Redis decr 原子扣减 → 库存不足立即返回失败
 * 扣减成功后 setnx 锁定本次序号 → 写入延迟队列异步同步 DB
 */
@Slf4j
@Service("activitySkuStockHandler")
public class ActivitySkuStockHandler extends AbstractActivityChainHandler {

    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean apply(ActivityFactorEntity factor) {

        String userId =  factor.getUserId();
        Long activityId =  factor.getActivityId();
        Long skuId =  factor.getSkuId();
        boolean stockOk = activityRepository.deductActivitySkuStock(activityId,skuId);
        if (!stockOk) {
            log.warn("SKU:{} 库存不足 activityId:{}",skuId, activityId);
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_EMPTY.getInfo());
        }

        return next(factor);
    }
}
