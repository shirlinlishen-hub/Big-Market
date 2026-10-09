package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;
import shirlin.ai.domain.Activity.model.aggregate.CreateSkuOrderAggregate;
import shirlin.ai.domain.Activity.service.ActivityEligibilityService;
import shirlin.ai.domain.Activity.service.Sku.ActivitySkuService;
import shirlin.ai.domain.strategy.adapter.repository.IRaffleOrderRepository;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.service.Raffle.RaffleOrderService;
import shirlin.ai.domain.strategy.service.Raffle.RaffleService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityValidationEntryTest {

    @Test
    void invalidActivityCannotReserveDrawQuota() {
        ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
        RaffleOrderService orders = mock(RaffleOrderService.class);
        RaffleService service = new RaffleService();
        ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
        ReflectionTestUtils.setField(service, "raffleOrderService", orders);
        when(eligibility.requireActive(20001L)).thenThrow(
                new AppException(ResponseCode.ACTIVITY_EXPIRED.getCode()));

        AppException exception = assertThrows(AppException.class,
                () -> service.doRaffle(drawFactor()));

        assertEquals(ResponseCode.ACTIVITY_EXPIRED.getCode(), exception.getCode());
        verifyNoInteractions(orders);
    }

    @Test
    void activeActivityWithoutStrategyCannotReserveDrawQuota() {
        ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
        RaffleOrderService orders = mock(RaffleOrderService.class);
        RaffleService service = new RaffleService();
        ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
        ReflectionTestUtils.setField(service, "raffleOrderService", orders);
        when(eligibility.requireActive(20001L)).thenReturn(ActivityEntity.builder()
                .activityId(20001L).status(1).build());

        AppException exception = assertThrows(AppException.class,
                () -> service.doRaffle(drawFactor()));

        assertEquals(ResponseCode.ACTIVITY_NOT_EXISTS.getCode(), exception.getCode());
        verifyNoInteractions(orders);
    }

    @Test
    void drawOrderUsesCanonicalValidationBeforeReservingQuota() {
        ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
        IRaffleOrderRepository repository = mock(IRaffleOrderRepository.class);
        RaffleOrderService service = new RaffleOrderService();
        ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
        ReflectionTestUtils.setField(service, "raffleOrderRepository", repository);
        ActivityFactorEntity factor = ActivityFactorEntity.builder()
                .userId("user-1")
                .activityId(20001L)
                .strategyId(100001L)
                .outBusinessNo("draw-request-1")
                .build();
        ActivityEntity validated = ActivityEntity.builder()
                .activityId(20001L).strategyId(100001L).status(1).build();
        DrawOrderEntity reserved = DrawOrderEntity.builder()
                .orderId("draw-order-1").build();
        when(eligibility.requireActive(20001L)).thenReturn(validated);
        when(repository.reserveDraw(factor)).thenReturn(reserved);

        DrawOrderEntity result = service.reserveDraw(factor);

        assertEquals("draw-order-1", result.getOrderId());
        verify(eligibility).requireActive(20001L);
        verify(repository).reserveDraw(factor);
    }

    @Test
    void purchaseUsesTheActivityReturnedByCanonicalValidation() {
        IActivityRepository repository = mock(IActivityRepository.class);
        ActivityEligibilityService eligibility = mock(ActivityEligibilityService.class);
        ActivitySkuService service = new ActivitySkuService();
        ReflectionTestUtils.setField(service, "activityRepository", repository);
        ReflectionTestUtils.setField(service, "activityEligibilityService", eligibility);
        ActivityEntity validated = ActivityEntity.builder()
                .activityId(20001L).strategyId(100001L).status(1).build();
        when(eligibility.requireActive(20001L)).thenReturn(validated);
        when(repository.queryActivitySku(30001L)).thenReturn(ActivitySkuEntity.builder()
                .skuId(30001L).activityId(20001L).activityCountId(10001L).status(1).build());
        when(repository.purchaseSkuAndGrant(any(), eq(10001L))).thenReturn(
                ActivityOrderEntity.builder().orderId("purchase-1").build());

        ActivityOrderEntity result = service.createOrder(purchaseFactor());

        assertEquals("purchase-1", result.getOrderId());
        verify(repository).purchaseSkuAndGrant(
                argThat((CreateSkuOrderAggregate aggregate) ->
                        aggregate.getActivity() == validated), eq(10001L));
        verify(repository, never()).queryActivityById(any());
    }

    private ActivityFactorEntity drawFactor() {
        return ActivityFactorEntity.builder()
                .userId("user-1")
                .activityId(20001L)
                .outBusinessNo("draw-request-1")
                .build();
    }

    private ActivityFactorEntity purchaseFactor() {
        return ActivityFactorEntity.builder()
                .userId("buyer-1")
                .activityId(20001L)
                .skuId(30001L)
                .outBusinessNo("payment-1")
                .build();
    }
}
