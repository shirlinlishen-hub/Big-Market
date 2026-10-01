package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.model.entity.QualificationRevokeResult;
import shirlin.ai.infrastructure.adapter.repository.ActivityRepository;
import shirlin.ai.infrastructure.adapter.repository.MysqlInventoryBucketService;
import shirlin.ai.infrastructure.adapter.repository.TransactionalOutboxService;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;
import shirlin.ai.infrastructure.dao.po.ActivityOrder;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ActivityRefundTest {
    @Test
    void finiteRefundRevokesUnusedRightsCancelsRebateAndRestoresStock() {
        IActivityOrderDao orders = mock(IActivityOrderDao.class);
        IActivityAccountDao accounts = mock(IActivityAccountDao.class);
        ISkuRebateDao rebates = mock(ISkuRebateDao.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        TransactionalOutboxService outbox = mock(TransactionalOutboxService.class);
        ActivityRepository repository = repository(orders, accounts, rebates, inventory, outbox);
        ActivityOrder order = order(5);
        ActivityAccount account = ActivityAccount.builder().totalCount(7).totalCountSurplus(3).build();
        SkuRebateOrder rebate = new SkuRebateOrder();
        rebate.setStatus(1); rebate.setRebateDrawCount(2);
        when(orders.selectByUserIdAndOutBusinessNoForUpdate("user-1", "pay-1")).thenReturn(order);
        when(rebates.selectForUpdate("purchase-1")).thenReturn(rebate);
        when(accounts.selectForUpdate("user-1", 20001L)).thenReturn(account);
        when(accounts.revokeFiniteRights("user-1", 20001L, 7, 3)).thenReturn(1);
        when(orders.markRefunded("purchase-1", "refund-1", 4, 3, 4)).thenReturn(1);
        when(orders.selectMaxActiveMonthCap("user-1", 20001L)).thenReturn(10);
        when(orders.selectMaxActiveDayCap("user-1", 20001L)).thenReturn(2);
        when(accounts.setPeriodLimits("user-1", 20001L, 10, 2)).thenReturn(1);

        QualificationRevokeResult result = repository.revokePurchase(
                "user-1", 20001L, 30001L, "pay-1", "refund-1");

        assertEquals(3, result.getRemovedUnusedCount());
        assertEquals(4, result.getConsumedExposureCount());
        verify(rebates).cancel("purchase-1");
        verify(inventory).releaseSku("pay-1");
        verify(outbox).append(eq("SKU_QUALIFICATION_REVOKED"), eq("sku-refund:refund-1"),
                eq("purchase-1"), eq("user-1"), anyMap());
    }

    @Test
    void unlimitedRefundQuarantinesAccountForManualReview() {
        IActivityOrderDao orders = mock(IActivityOrderDao.class);
        IActivityAccountDao accounts = mock(IActivityAccountDao.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        TransactionalOutboxService outbox = mock(TransactionalOutboxService.class);
        ActivityRepository repository = repository(orders, accounts, mock(ISkuRebateDao.class),
                inventory, outbox);
        ActivityOrder order = order(-1);
        ActivityAccount account = ActivityAccount.builder().totalCount(-1).totalCountSurplus(-1).build();
        when(orders.selectByUserIdAndOutBusinessNoForUpdate("user-1", "pay-1")).thenReturn(order);
        when(accounts.selectForUpdate("user-1", 20001L)).thenReturn(account);
        when(accounts.quarantineSurplus("user-1", 20001L)).thenReturn(1);
        when(orders.markRefunded("purchase-1", "refund-1", 5, 0, -1)).thenReturn(1);
        when(orders.selectMaxActiveMonthCap("user-1", 20001L)).thenReturn(0);
        when(orders.selectMaxActiveDayCap("user-1", 20001L)).thenReturn(0);
        when(accounts.setPeriodLimits("user-1", 20001L, 0, 0)).thenReturn(1);

        QualificationRevokeResult result = repository.revokePurchase(
                "user-1", 20001L, 30001L, "pay-1", "refund-1");

        assertTrue(result.isManualReview());
        verify(accounts).quarantineSurplus("user-1", 20001L);
        verify(inventory).releaseSku("pay-1");
        verify(outbox).append(eq("SKU_REFUND_MANUAL_REVIEW"), eq("sku-refund:refund-1"),
                eq("purchase-1"), eq("user-1"), anyMap());
    }

    private ActivityRepository repository(IActivityOrderDao orders, IActivityAccountDao accounts,
                                          ISkuRebateDao rebates,
                                          MysqlInventoryBucketService inventory,
                                          TransactionalOutboxService outbox) {
        ActivityRepository repository = new ActivityRepository();
        ReflectionTestUtils.setField(repository, "activityOrderDao", orders);
        ReflectionTestUtils.setField(repository, "activityAccountDao", accounts);
        ReflectionTestUtils.setField(repository, "skuRebateDao", rebates);
        ReflectionTestUtils.setField(repository, "inventoryBucketService", inventory);
        ReflectionTestUtils.setField(repository, "outboxService", outbox);
        return repository;
    }

    private ActivityOrder order(int grant) {
        return ActivityOrder.builder().orderId("purchase-1").userId("user-1")
                .activityId(20001L).skuId(30001L).orderStatus(3)
                .grantTotalCount(grant).grantMonthCount(10).grantDayCount(2).build();
    }
}
