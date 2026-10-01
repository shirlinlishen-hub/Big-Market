package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.service.Sku.ActivitySkuService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ActivitySkuPurchaseTest {
    @Test
    void purchaseGrantsConfiguredDrawsWithoutConsumingOne() {
        IActivityRepository repository = mock(IActivityRepository.class);
        ActivitySkuService service = new ActivitySkuService();
        ReflectionTestUtils.setField(service, "activityRepository", repository);
        ActivityFactorEntity factor = ActivityFactorEntity.builder()
                .userId("buyer-1").activityId(20001L).strategyId(100001L)
                .skuId(30001L).outBusinessNo("payment-123").build();
        when(repository.queryActivityById(20001L)).thenReturn(ActivityEntity.builder()
                .activityId(20001L).strategyId(100001L).status(1).build());
        when(repository.queryActivitySku(30001L)).thenReturn(ActivitySkuEntity.builder()
                .skuId(30001L).activityId(20001L).activityCountId(10001L).status(1).build());
        when(repository.purchaseSkuAndGrant(any(), eq(10001L))).thenReturn(
                ActivityOrderEntity.builder().orderId("purchase-1").build());

        ActivityOrderEntity order = service.createOrder(factor);

        assertEquals("purchase-1", order.getOrderId());
        verify(repository).purchaseSkuAndGrant(any(), eq(10001L));
    }
}
