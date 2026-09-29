package shirlin.ai.test.Messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.IInboxEventDao;
import shirlin.ai.infrastructure.dao.po.InboxEvent;
import shirlin.ai.infrastructure.delivery.AwardDeliveryApplicationService;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;
import shirlin.ai.infrastructure.delivery.DeliveryResult;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AwardDeliveryApplicationServiceTest {

    @Test
    void newMessageRegistersInboxRunsFulfillmentAndCommitsSuccess() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        when(fixture.fulfillment.fulfill("user-1", "order-1"))
                .thenReturn(FulfillmentResult.SUCCESS);
        when(fixture.inbox.markSuccess(
                AwardDeliveryApplicationService.CONSUMER_NAME, "event-1"))
                .thenReturn(1);

        DeliveryResult result = fixture.service.deliver(event());

        assertEquals(DeliveryResult.PROCESSED, result);
        verify(fixture.fulfillment).fulfill("user-1", "order-1");
        verify(fixture.inbox).markSuccess(
                AwardDeliveryApplicationService.CONSUMER_NAME, "event-1");
    }

    @Test
    void successfulInboxWithSameIdentityIsDuplicateNoOp() {
        Fixture fixture = fixtureWithInboxStatus(1, false);

        assertEquals(DeliveryResult.DUPLICATE, fixture.service.deliver(event()));

        verifyNoInteractions(fixture.fulfillment);
        verify(fixture.inbox, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void sameEventIdWithDifferentPayloadHashIsPermanentConflict() {
        Fixture fixture = fixtureWithInboxStatus(1, true);

        assertThrows(NonRetryableDeliveryException.class,
                () -> fixture.service.deliver(event()));

        verifyNoInteractions(fixture.fulfillment);
    }

    @Test
    void invalidUserRelationshipIsRejectedBeforeInboxWrite() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> mismatched =
                new DomainEventEnvelope<>("event-1", "AWARD_DELIVERY_REQUESTED",
                        "award-delivery:order-1:v0", 1, "order-1", "user-2",
                        Instant.parse("2026-09-28T12:00:00Z"),
                        new AwardDeliveryRequestedPayload("order-1", "user-1"));

        assertThrows(NonRetryableDeliveryException.class,
                () -> fixture.service.deliver(mismatched));

        verifyNoInteractions(fixture.inbox, fixture.fulfillment);
    }

    @Test
    void missingTaskFailureRollsBackApplicationFlow() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        when(fixture.fulfillment.fulfill("user-1", "order-1"))
                .thenThrow(new NonRetryableDeliveryException("task missing"));

        assertThrows(NonRetryableDeliveryException.class,
                () -> fixture.service.deliver(event()));

        verify(fixture.inbox, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void alreadySuccessfulTaskStillCompletesNewInboxRecord() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        when(fixture.fulfillment.fulfill("user-1", "order-1"))
                .thenReturn(FulfillmentResult.SUCCESS);
        when(fixture.inbox.markSuccess(anyString(), anyString())).thenReturn(1);

        assertEquals(DeliveryResult.PROCESSED, fixture.service.deliver(event()));

        verify(fixture.inbox).markSuccess(
                AwardDeliveryApplicationService.CONSUMER_NAME, "event-1");
    }

    @Test
    void manualFulfillmentIsConsumedSuccessfullyWithoutMessageRetry() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        when(fixture.fulfillment.fulfill("user-1", "order-1"))
                .thenReturn(FulfillmentResult.MANUAL);
        when(fixture.inbox.markSuccess(anyString(), anyString())).thenReturn(1);

        assertEquals(DeliveryResult.PROCESSED, fixture.service.deliver(event()));

        verify(fixture.inbox).markSuccess(
                AwardDeliveryApplicationService.CONSUMER_NAME, "event-1");
    }

    @Test
    void inboxSuccessUpdateRaceIsRetryable() {
        Fixture fixture = fixtureWithInboxStatus(0, false);
        when(fixture.fulfillment.fulfill("user-1", "order-1"))
                .thenReturn(FulfillmentResult.SUCCESS);
        when(fixture.inbox.markSuccess(anyString(), anyString())).thenReturn(0);

        assertThrows(RetryableDeliveryException.class,
                () -> fixture.service.deliver(event()));
    }

    private static Fixture fixtureWithInboxStatus(int status, boolean corruptHash) {
        IInboxEventDao inbox = mock(IInboxEventDao.class);
        AwardFulfillmentService fulfillment = mock(AwardFulfillmentService.class);
        AtomicReference<InboxEvent> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            InboxEvent inserted = invocation.getArgument(0);
            InboxEvent selected = new InboxEvent();
            selected.setConsumerName(inserted.getConsumerName());
            selected.setEventId(inserted.getEventId());
            selected.setEventKey(inserted.getEventKey());
            selected.setAggregateId(inserted.getAggregateId());
            selected.setPayloadHash(corruptHash ? "0".repeat(64) : inserted.getPayloadHash());
            selected.setStatus(status);
            stored.set(selected);
            return 1;
        }).when(inbox).insertOrTouch(any(InboxEvent.class));
        when(inbox.selectForUpdate(
                AwardDeliveryApplicationService.CONSUMER_NAME, "event-1"))
                .thenAnswer(invocation -> stored.get());
        AwardDeliveryApplicationService service = new AwardDeliveryApplicationService(
                inbox, fulfillment, new ObjectMapper());
        return new Fixture(inbox, fulfillment, service);
    }

    private static DomainEventEnvelope<AwardDeliveryRequestedPayload> event() {
        return new DomainEventEnvelope<>("event-1", "AWARD_DELIVERY_REQUESTED",
                "award-delivery:order-1:v0", 1, "order-1", "user-1",
                Instant.parse("2026-09-28T12:00:00Z"),
                new AwardDeliveryRequestedPayload("order-1", "user-1"));
    }

    private record Fixture(IInboxEventDao inbox,
                           AwardFulfillmentService fulfillment,
                           AwardDeliveryApplicationService service) {
    }
}
