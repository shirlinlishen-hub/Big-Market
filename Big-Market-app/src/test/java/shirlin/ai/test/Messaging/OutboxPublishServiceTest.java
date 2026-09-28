package shirlin.ai.test.Messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;
import shirlin.ai.infrastructure.messaging.IMessagePublisher;
import shirlin.ai.infrastructure.messaging.OutboxClaimService;
import shirlin.ai.infrastructure.messaging.OutboxPublishService;
import shirlin.ai.infrastructure.messaging.exception.MessagePublishException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublishServiceTest {

    @Test
    void mapsOutboxToEnvelopeAndMarksOnlyConfirmedLease() {
        Fixture fixture = fixture();
        OutboxEvent event = event("event-1", "token-1",
                "{\"orderId\":\"order-1\",\"userId\":\"user-1\"}");
        when(fixture.claims.claimBatch("worker-1", 100, Duration.ofSeconds(30)))
                .thenReturn(List.of(event));
        when(fixture.dao.markPublished("event-1", "token-1")).thenReturn(1);

        int published = fixture.service.publishBatch(
                "worker-1", 100, Duration.ofSeconds(30));

        assertEquals(1, published);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEventEnvelope<AwardDeliveryRequestedPayload>> envelope =
                ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(fixture.publisher).publish(envelope.capture());
        DomainEventEnvelope<AwardDeliveryRequestedPayload> value = envelope.getValue();
        assertEquals("event-1", value.eventId());
        assertEquals("AWARD_DELIVERY_REQUESTED", value.eventType());
        assertEquals("award-delivery:order-1:v0", value.eventKey());
        assertEquals(1, value.schemaVersion());
        assertEquals("order-1", value.aggregateId());
        assertEquals("user-1", value.partitionKey());
        assertEquals(Instant.parse("2026-09-28T12:00:00Z"), value.occurredAt());
        assertEquals("order-1", value.payload().orderId());
        assertEquals("user-1", value.payload().userId());
        verify(fixture.dao).markPublished("event-1", "token-1");
    }

    @Test
    void recordsTruncatedFailureAndLeavesEventPending() {
        Fixture fixture = fixture();
        OutboxEvent event = event("event-1", "token-1",
                "{\"orderId\":\"order-1\",\"userId\":\"user-1\"}");
        when(fixture.claims.claimBatch(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(List.of(event));
        doThrow(new MessagePublishException("x".repeat(600)))
                .when(fixture.publisher).publish(any());

        assertEquals(0, fixture.service.publishBatch(
                "worker-1", 100, Duration.ofSeconds(30)));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(fixture.dao).recordPublishFailure(
                eq("event-1"), eq("token-1"), reason.capture());
        assertEquals(500, reason.getValue().length());
        verify(fixture.dao, never()).markPublished(anyString(), anyString());
    }

    @Test
    void stalePublisherCannotMarkLeaseOwnedByAnotherWorker() {
        Fixture fixture = fixture();
        OutboxEvent event = event("event-1", "old-token",
                "{\"orderId\":\"order-1\",\"userId\":\"user-1\"}");
        when(fixture.claims.claimBatch(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(List.of(event));
        when(fixture.dao.markPublished("event-1", "old-token")).thenReturn(0);

        assertEquals(0, fixture.service.publishBatch(
                "worker-1", 100, Duration.ofSeconds(30)));

        verify(fixture.publisher).publish(any());
        verify(fixture.dao, never()).recordPublishFailure(anyString(), anyString(), anyString());
    }

    @Test
    void malformedPayloadDoesNotBlockOtherEventsInBatch() {
        Fixture fixture = fixture();
        OutboxEvent malformed = event("event-1", "token-1", "not-json");
        OutboxEvent valid = event("event-2", "token-1",
                "{\"orderId\":\"order-2\",\"userId\":\"user-2\"}");
        valid.setEventKey("award-delivery:order-2:v0");
        valid.setAggregateId("order-2");
        valid.setPartitionKey("user-2");
        when(fixture.claims.claimBatch(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(List.of(malformed, valid));
        when(fixture.dao.markPublished("event-2", "token-1")).thenReturn(1);

        assertEquals(1, fixture.service.publishBatch(
                "worker-1", 100, Duration.ofSeconds(30)));

        verify(fixture.dao).recordPublishFailure(eq("event-1"), eq("token-1"), anyString());
        verify(fixture.dao).markPublished("event-2", "token-1");
        verify(fixture.publisher, times(1)).publish(any());
    }

    private static Fixture fixture() {
        OutboxClaimService claims = mock(OutboxClaimService.class);
        IMessagePublisher publisher = mock(IMessagePublisher.class);
        IOutboxEventDao dao = mock(IOutboxEventDao.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new Fixture(claims, publisher, dao,
                new OutboxPublishService(claims, publisher, dao, objectMapper));
    }

    private static OutboxEvent event(String eventId, String token, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType("AWARD_DELIVERY_REQUESTED");
        event.setEventKey("award-delivery:order-1:v0");
        event.setAggregateId("order-1");
        event.setPartitionKey("user-1");
        event.setPayload(payload);
        event.setLockToken(token);
        event.setCreateTime(Date.from(Instant.parse("2026-09-28T12:00:00Z")));
        return event;
    }

    private record Fixture(OutboxClaimService claims,
                           IMessagePublisher publisher,
                           IOutboxEventDao dao,
                           OutboxPublishService service) {
    }
}
