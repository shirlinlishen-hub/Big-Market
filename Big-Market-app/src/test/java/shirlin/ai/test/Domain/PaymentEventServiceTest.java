package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.*;
import shirlin.ai.domain.Activity.service.IActivitySkuService;
import shirlin.ai.infrastructure.adapter.repository.PaymentEventService;
import shirlin.ai.infrastructure.dao.IPaymentEventDao;
import shirlin.ai.infrastructure.dao.po.PaymentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentEventServiceTest {
    @Test
    void duplicateSuccessfulNotificationReturnsOriginalResultWithoutGrantingAgain() {
        IPaymentEventDao events = mock(IPaymentEventDao.class);
        IActivitySkuService sku = mock(IActivitySkuService.class);
        PaymentEventService service = service(events, sku, mock(IActivityRepository.class));
        PaymentEvent stored = new PaymentEvent();
        stored.setEventId("evt-1"); stored.setPayloadHash("hash-1"); stored.setStatus(1);
        stored.setPurchaseOrderId("purchase-1");
        when(events.selectByEventId("evt-1")).thenReturn(stored);

        PaymentEventResult result = service.process(command("PAYMENT_SUCCEEDED"));

        assertEquals("purchase-1", result.getPurchaseOrderId());
        verifyNoInteractions(sku);
    }

    @Test
    void reusedEventIdWithDifferentPayloadIsRejected() {
        IPaymentEventDao events = mock(IPaymentEventDao.class);
        PaymentEventService service = service(events, mock(IActivitySkuService.class),
                mock(IActivityRepository.class));
        PaymentEvent stored = new PaymentEvent();
        stored.setEventId("evt-1"); stored.setPayloadHash("different"); stored.setStatus(1);
        when(events.selectByEventId("evt-1")).thenReturn(stored);

        assertThrows(RuntimeException.class, () -> service.process(command("PAYMENT_SUCCEEDED")));
    }

    @Test
    void refundPersistsRemovedAndConsumedCounts() {
        IPaymentEventDao events = mock(IPaymentEventDao.class);
        IActivityRepository activities = mock(IActivityRepository.class);
        PaymentEventService service = service(events, mock(IActivitySkuService.class), activities);
        when(events.insertIgnore(any())).thenReturn(1);
        when(events.markResult("evt-1", 1, "purchase-1", 3, 2)).thenReturn(1);
        when(activities.revokePurchase("user-1", 20001L, 30001L, "pay-1", "evt-1"))
                .thenReturn(QualificationRevokeResult.builder().purchaseOrderId("purchase-1")
                        .removedUnusedCount(3).consumedExposureCount(2).build());

        PaymentEventResult result = service.process(command("REFUNDED"));

        assertEquals(3, result.getRemovedUnusedCount());
        assertEquals(2, result.getConsumedExposureCount());
    }

    private PaymentEventService service(IPaymentEventDao events, IActivitySkuService sku,
                                        IActivityRepository activities) {
        PaymentEventService service = new PaymentEventService();
        ReflectionTestUtils.setField(service, "eventDao", events);
        ReflectionTestUtils.setField(service, "skuService", sku);
        ReflectionTestUtils.setField(service, "activityRepository", activities);
        return service;
    }

    private PaymentEventCommand command(String type) {
        return PaymentEventCommand.builder().eventId("evt-1").eventType(type)
                .paymentOrderNo("pay-1").userId("user-1").activityId(20001L)
                .skuId(30001L).payloadHash("hash-1").build();
    }
}
