package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.aggregate.CreateSkuOrderAggregate;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.model.entity.QualificationRevokeResult;
import shirlin.ai.domain.Activity.service.RefundQualificationPolicy;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.Activity;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;
import shirlin.ai.infrastructure.dao.po.ActivitySku;
import shirlin.ai.infrastructure.dao.po.ActivityCount;
import shirlin.ai.infrastructure.dao.po.ActivityOrder;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;
import shirlin.ai.infrastructure.redis.IRedisService;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.util.*;

@Repository
public class ActivityRepository implements IActivityRepository {

    private static final String ACTIVITY_KEY = "big_market:activity:activity";

    @Resource
    private IActivityDao activityDao;

    @Resource
    private IActivitySkuDao activitySkuDao;

    @Resource
    private IActivityAccountDao activityAccountDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    @Resource
    private IActivityOrderDao activityOrderDao;

    @Resource
    private IActivityCountDao activityCountDao;
    @Resource
    private ISkuRebateDao skuRebateDao;

    @Resource
    private SnowflakeIdGenerator idGenerator;

    @Resource
    private IRedisService redisService;
    @Resource
    private MysqlInventoryBucketService inventoryBucketService;
    @Resource
    private TransactionalOutboxService outboxService;

    private Long queryActivityId(Long activityId) {
        Activity activity = activityDao.selectByStrategyId(activityId);
        if (activity == null) {
            throw new AppException(ResponseCode.ACTIVITY_NOT_EXISTS.getInfo());
        }
        return activity.getActivityId();
    }

    @Override
    public void cacheActivity(ActivityEntity activity) {
        redisService.setValue(ACTIVITY_KEY + activity.getActivityId(), activity);
    }

    @Override
    public ActivityEntity cacheGetActivity(Long activityId) {
        return redisService.getValue(ACTIVITY_KEY + activityId);

    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityOrderEntity purchaseSkuAndGrant(CreateSkuOrderAggregate aggregate, Long activityCountId) {
        String userId = aggregate.getFactor().getUserId();
        String businessNo = aggregate.getFactor().getOutBusinessNo();
        Long activityId = aggregate.getActivity().getActivityId();
        Long skuId = aggregate.getSku().getSkuId();
        ActivityOrder existing = activityOrderDao.selectByOutBusinessNo(businessNo);
        if (existing != null) {
            if (!userId.equals(existing.getUserId()) || !activityId.equals(existing.getActivityId())
                    || !skuId.equals(existing.getSkuId())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
            }
            return toOrderEntity(existing);
        }
        ActivityCount count = activityCountDao.selectByActivityCountId(activityCountId);
        if (count == null || count.getTotalCount() == null || count.getMonthCount() == null
                || count.getDayCount() == null || count.getTotalCount() == 0
                || count.getTotalCount() < -1 || count.getMonthCount() == 0
                || count.getMonthCount() < -1 || count.getDayCount() == 0
                || count.getDayCount() < -1) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
        }
        if (!inventoryBucketService.reserveSku(skuId, businessNo, aggregate.getSku().getStockCount())) {
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_EMPTY.getCode());
        }
        ActivityOrder order = ActivityOrder.builder()
                .orderId(String.valueOf(idGenerator.nextId()))
                .userId(userId)
                .activityId(activityId)
                .skuId(skuId)
                .strategyId(aggregate.getActivity().getStrategyId())
                .orderStatus(3) // GRANTED: one purchase may fund multiple draws
                .pointsCost(aggregate.getSku().getPointsCost())
                .outBusinessNo(businessNo)
                .grantTotalCount(count.getTotalCount())
                .grantMonthCount(count.getMonthCount())
                .grantDayCount(count.getDayCount())
                .build();
        if (activityOrderDao.insert(order) != 1) {
            throw new IllegalStateException("Failed to persist purchase order");
        }
        if (activityAccountDao.grantDrawRights(userId, activityId,
                count.getTotalCount(), count.getMonthCount(), count.getDayCount()) <= 0) {
            throw new IllegalStateException("Failed to grant draw qualification");
        }
        Integer rebateCount = skuRebateDao.selectConfiguredCount(skuId);
        if (rebateCount != null) {
            if (rebateCount <= 0) throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode());
            SkuRebateOrder rebate = new SkuRebateOrder();
            rebate.setPurchaseOrderId(order.getOrderId());
            rebate.setUserId(userId);
            rebate.setActivityId(activityId);
            rebate.setRebateDrawCount(rebateCount);
            if (skuRebateDao.insertOrder(rebate) != 1) {
                throw new IllegalStateException("Failed to persist rebate task");
            }
        }
        outboxService.append("SKU_QUALIFICATION_GRANTED", "sku-granted:" + businessNo,
                order.getOrderId(), userId, Map.of(
                        "schemaVersion", 1,
                        "purchaseOrderId", order.getOrderId(),
                        "paymentOrderNo", businessNo,
                        "userId", userId,
                        "activityId", activityId,
                        "skuId", skuId,
                        "grantCount", count.getTotalCount()));
        return toOrderEntity(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualificationRevokeResult revokePurchase(String userId, Long activityId, Long skuId,
                                                     String paymentOrderNo, String refundEventId) {
        ActivityOrder order = activityOrderDao.selectByUserIdAndOutBusinessNoForUpdate(userId, paymentOrderNo);
        if (order == null || !activityId.equals(order.getActivityId()) || !skuId.equals(order.getSkuId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Original purchase was not found");
        }
        if (order.getOrderStatus() == 4 || order.getOrderStatus() == 5) {
            return QualificationRevokeResult.builder().purchaseOrderId(order.getOrderId())
                    .manualReview(order.getOrderStatus() == 5)
                    .removedUnusedCount(value(order.getRefundRemovedCount()))
                    .consumedExposureCount(value(order.getRefundExposureCount())).build();
        }
        if (order.getOrderStatus() != 3) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Purchase is not refundable");
        }

        SkuRebateOrder rebate = skuRebateDao.selectForUpdate(order.getOrderId());
        int completedRebate = rebate != null && Integer.valueOf(1).equals(rebate.getStatus())
                ? rebate.getRebateDrawCount() : 0;
        ActivityAccount account = activityAccountDao.selectForUpdate(userId, activityId);
        if (account == null) throw new IllegalStateException("Draw account is missing");

        boolean manual = false;
        int removed = 0;
        int exposure = 0;
        try {
            RefundQualificationPolicy.Decision decision = RefundQualificationPolicy.decide(
                    order.getGrantTotalCount(), completedRebate, account.getTotalCountSurplus());
            removed = decision.removedUnusedCount();
            exposure = decision.consumedExposureCount();
            if (activityAccountDao.revokeFiniteRights(userId, activityId,
                    decision.grantedCount(), decision.removedUnusedCount()) != 1) {
                throw new IllegalStateException("Failed to revoke draw qualification");
            }
        } catch (IllegalArgumentException e) {
            manual = true;
            exposure = -1;
            if (activityAccountDao.quarantineSurplus(userId, activityId) != 1) {
                throw new IllegalStateException("Failed to quarantine unlimited qualification", e);
            }
        }

        if (rebate != null) skuRebateDao.cancel(order.getOrderId());
        int refundStatus = manual ? 5 : 4;
        if (activityOrderDao.markRefunded(order.getOrderId(), refundEventId, refundStatus,
                removed, exposure) != 1) {
            throw new IllegalStateException("Purchase refund state changed");
        }
        inventoryBucketService.releaseSku(paymentOrderNo);
        Integer monthCap = activityOrderDao.selectMaxActiveMonthCap(userId, activityId);
        Integer dayCap = activityOrderDao.selectMaxActiveDayCap(userId, activityId);
        if (activityAccountDao.setPeriodLimits(userId, activityId,
                monthCap == null ? 0 : monthCap, dayCap == null ? 0 : dayCap) != 1) {
            throw new IllegalStateException("Failed to recalculate period limits");
        }
        String eventType = manual ? "SKU_REFUND_MANUAL_REVIEW" : "SKU_QUALIFICATION_REVOKED";
        outboxService.append(eventType, "sku-refund:" + refundEventId, order.getOrderId(),
                userId, Map.of(
                        "schemaVersion", 1,
                        "purchaseOrderId", order.getOrderId(),
                        "paymentOrderNo", paymentOrderNo,
                        "refundEventId", refundEventId,
                        "userId", userId,
                        "activityId", activityId,
                        "skuId", skuId,
                        "removedUnusedCount", removed,
                        "consumedExposureCount", exposure,
                        "manualReview", manual));
        return QualificationRevokeResult.builder().purchaseOrderId(order.getOrderId())
                .manualReview(manual).removedUnusedCount(removed)
                .consumedExposureCount(exposure).build();
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private ActivityOrderEntity toOrderEntity(ActivityOrder order) {
        return ActivityOrderEntity.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .activityId(order.getActivityId())
                .skuId(order.getSkuId())
                .strategyId(order.getStrategyId())
                .orderStatus(order.getOrderStatus())
                .pointsCost(order.getPointsCost())
                .outBusinessNo(order.getOutBusinessNo())
                .grantTotalCount(order.getGrantTotalCount())
                .grantMonthCount(order.getGrantMonthCount())
                .grantDayCount(order.getGrantDayCount())
                .refundRemovedCount(order.getRefundRemovedCount())
                .refundExposureCount(order.getRefundExposureCount())
                .build();
    }

    @Override
    public void updateOrderUsed(String orderId) {
        activityOrderDao.updateOrderStatus(orderId, 1);
    }

    @Override
    public ActivityOrderEntity queryUnusedOrder(String userId, Long skuId) {
        return activityOrderDao.queryUnusedOrder(userId,skuId);
    }

    @Override
    public ActivityEntity queryActivityById(Long activityId) {
        Activity res = activityDao.selectByActivityId(activityId);
        if (res == null) return null;
        return    ActivityEntity.builder()
                      .activityId(res.getActivityId())
                      .strategyId(res.getStrategyId())
                      .status(res.getStatus())
                      .beginTime(res.getBeginTime())
                      .endTime(res.getEndTime())
                      .build();
    }

    @Override
    public List<ActivitySkuEntity> querySkuByActivityId(Long activityId) {
        List<ActivitySku> res = activitySkuDao.selectByActivityId(activityId);
        List<ActivitySkuEntity> ans = new ArrayList<>();
        for (ActivitySku sku : res) {
            ActivitySkuEntity req = ActivitySkuEntity.builder()
                        .skuId(sku.getSkuId())
                        .activityId(sku.getActivityId())
                        .skuType(sku.getSkuType())
                        .pointsCost(sku.getPointsCost())
                        .activityCountId(sku.getActivityCountId())
                        .stockCount(sku.getStockCount())
                        .stockSurplus(sku.getStockSurplus())
                        .status(sku.getStatus())
                        .build();
            ans.add(req);
        }
        return ans;
    }

    @Override
    public ActivitySkuEntity queryActivitySku(Long skuId) {
        ActivitySku res = activitySkuDao.selectBySkuId(skuId);
        if (res == null) return null;
        return ActivitySkuEntity.builder()
                  .skuId(res.getSkuId())
                  .activityId(res.getActivityId())
                  .skuType(res.getSkuType())
                  .pointsCost(res.getPointsCost())
                  .activityCountId(res.getActivityCountId())
                  .stockCount(res.getStockCount())
                  .stockSurplus(res.getStockSurplus())
                  .status(res.getStatus())
                  .build();
    }

}
