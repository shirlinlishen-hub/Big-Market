package shirlin.ai.domain.Activity.adapter.repository;

import shirlin.ai.domain.Activity.model.aggregate.CreateSkuOrderAggregate;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.model.entity.QualificationRevokeResult;

import java.util.List;

public interface IActivityRepository {

    /**
     * 活动上线将Activity配置信息写入Redis
     * @param activity
     */
    void cacheActivity(ActivityEntity activity);


    /**
     * 从缓存中获取活动配置信息
     * @param activityId
     * @return
     */
    ActivityEntity cacheGetActivity(Long activityId);

    // ---- 参与订单（Phase 2 事务内） ----

    /** Persist the purchase, reserve SKU stock and grant configured draw rights atomically. */
    ActivityOrderEntity purchaseSkuAndGrant(CreateSkuOrderAggregate aggregate, Long activityCountId);

    QualificationRevokeResult revokePurchase(String userId, Long activityId, Long skuId,
                                              String paymentOrderNo, String refundEventId);

    /**
     * 消费完sku并将状态改为 1（completed）
     */
    void updateOrderUsed(String orderId);

    /**
     * 查找为完成的订单
     * @param userId
     * @param skuId
     * @return
     */
    ActivityOrderEntity queryUnusedOrder(String userId, Long skuId);

    /**
     * 查找Activity配置信息
     * @param activityId
     * @return
     */
    ActivityEntity queryActivityById(Long activityId);

    /**
     * 查找活动的全部Sku信息
     *
     * @param activityId
     * @return
     */
    List<ActivitySkuEntity> querySkuByActivityId(Long activityId);

    /**
     * 查询指定SkuId的sku信息
     * @param skuId
     * @return
     */
    ActivitySkuEntity queryActivitySku(Long skuId);
}
