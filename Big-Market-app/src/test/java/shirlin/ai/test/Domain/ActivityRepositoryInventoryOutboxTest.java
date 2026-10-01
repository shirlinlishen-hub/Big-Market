package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.model.aggregate.CreateSkuOrderAggregate;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.infrastructure.adapter.repository.ActivityRepository;
import shirlin.ai.infrastructure.adapter.repository.MysqlInventoryBucketService;
import shirlin.ai.infrastructure.adapter.repository.TransactionalOutboxService;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IActivityCountDao;
import shirlin.ai.infrastructure.dao.IActivityOrderDao;
import shirlin.ai.infrastructure.dao.ISkuRebateDao;
import shirlin.ai.infrastructure.dao.po.ActivityCount;
import shirlin.ai.infrastructure.dao.po.ActivityOrder;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityRepositoryInventoryOutboxTest {
    @Test
    void duplicatePaymentReservesMysqlInventoryAndWritesOutboxOnlyOnce() {
        IActivityOrderDao orders = mock(IActivityOrderDao.class);
        IActivityCountDao counts = mock(IActivityCountDao.class);
        IActivityAccountDao accounts = mock(IActivityAccountDao.class);
        ISkuRebateDao rebates = mock(ISkuRebateDao.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        TransactionalOutboxService outbox = mock(TransactionalOutboxService.class);
        SnowflakeIdGenerator ids = mock(SnowflakeIdGenerator.class);
        ActivityRepository repository = new ActivityRepository();
        ReflectionTestUtils.setField(repository, "activityOrderDao", orders);
        ReflectionTestUtils.setField(repository, "activityCountDao", counts);
        ReflectionTestUtils.setField(repository, "activityAccountDao", accounts);
        ReflectionTestUtils.setField(repository, "skuRebateDao", rebates);
        ReflectionTestUtils.setField(repository, "inventoryBucketService", inventory);
        ReflectionTestUtils.setField(repository, "outboxService", outbox);
        ReflectionTestUtils.setField(repository, "idGenerator", ids);

        ActivityOrder persisted = ActivityOrder.builder().orderId("purchase-1")
                .userId("buyer-1").activityId(20001L).skuId(30001L).strategyId(100001L)
                .orderStatus(3).outBusinessNo("payment-123")
                .grantTotalCount(5).grantMonthCount(3).grantDayCount(1).build();
        when(orders.selectByOutBusinessNo("payment-123")).thenReturn(null, persisted);
        when(ids.nextId()).thenReturn(9001L);
        ActivityCount count = new ActivityCount();
        count.setTotalCount(5); count.setMonthCount(3); count.setDayCount(1);
        when(counts.selectByActivityCountId(10001L)).thenReturn(count);
        when(inventory.reserveSku(30001L, "payment-123", 100L)).thenReturn(true);
        when(orders.insert(any())).thenReturn(1);
        when(accounts.grantDrawRights("buyer-1", 20001L, 5, 3, 1)).thenReturn(1);
        when(rebates.selectConfiguredCount(30001L)).thenReturn(null);

        CreateSkuOrderAggregate aggregate = CreateSkuOrderAggregate.builder()
                .factor(ActivityFactorEntity.builder().userId("buyer-1").activityId(20001L)
                        .skuId(30001L).outBusinessNo("payment-123").build())
                .activity(ActivityEntity.builder().activityId(20001L).strategyId(100001L).build())
                .sku(ActivitySkuEntity.builder().skuId(30001L).stockCount(100L).build())
                .build();

        ActivityOrderEntity first = repository.purchaseSkuAndGrant(aggregate, 10001L);
        ActivityOrderEntity replay = repository.purchaseSkuAndGrant(aggregate, 10001L);

        assertEquals("9001", first.getOrderId());
        assertEquals("purchase-1", replay.getOrderId());
        verify(inventory, times(1)).reserveSku(30001L, "payment-123", 100L);
        verify(accounts, times(1)).grantDrawRights("buyer-1", 20001L, 5, 3, 1);
        verify(outbox, times(1)).append(eq("SKU_QUALIFICATION_GRANTED"),
                eq("sku-granted:payment-123"), eq("9001"), eq("buyer-1"), anyMap());
    }
}
