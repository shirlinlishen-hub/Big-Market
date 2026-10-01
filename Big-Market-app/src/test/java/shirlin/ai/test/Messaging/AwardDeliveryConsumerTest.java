package shirlin.ai.test.Messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.retry.support.RetryTemplate;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.delivery.AwardDeliveryApplicationService;
import shirlin.ai.infrastructure.delivery.DeliveryFailureRecorder;
import shirlin.ai.infrastructure.delivery.DeliveryResult;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;
import shirlin.ai.messaging.AwardDeliveryConsumer;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AwardDeliveryConsumerTest {

    @Test
    void successfulAndDuplicateMessagesReturnNormallyForContainerAck() throws Exception {
        AwardDeliveryApplicationService service = mock(AwardDeliveryApplicationService.class);
        DeliveryFailureRecorder failures = mock(DeliveryFailureRecorder.class);
        AwardDeliveryConsumer consumer = consumer(service, failures);
        when(service.deliver(any())).thenReturn(
                DeliveryResult.PROCESSED, DeliveryResult.DUPLICATE);
        Message message = message(event());

        assertDoesNotThrow(() -> consumer.consume(message));
        assertDoesNotThrow(() -> consumer.consume(message));

        verify(service, times(2)).deliver(any());
        verifyNoInteractions(failures);
    }

    @Test
    void retryableFailureIsRecordedAndRethrownForRetryAdvice() throws Exception {
        AwardDeliveryApplicationService service = mock(AwardDeliveryApplicationService.class);
        DeliveryFailureRecorder failures = mock(DeliveryFailureRecorder.class);
        AwardDeliveryConsumer consumer = consumer(service, failures);
        RetryableDeliveryException expected =
                new RetryableDeliveryException("database unavailable");
        when(service.deliver(any())).thenThrow(expected);

        RetryableDeliveryException actual = assertThrows(
                RetryableDeliveryException.class, () -> consumer.consume(message(event())));

        assertSame(expected, actual);
        verify(failures).recordFailure("order-1", "database unavailable");
    }

    @Test
    void permanentFailureIsRecordedAndRejectedWithoutRequeue() throws Exception {
        AwardDeliveryApplicationService service = mock(AwardDeliveryApplicationService.class);
        DeliveryFailureRecorder failures = mock(DeliveryFailureRecorder.class);
        AwardDeliveryConsumer consumer = consumer(service, failures);
        when(service.deliver(any())).thenThrow(
                new NonRetryableDeliveryException("identity mismatch"));

        AmqpRejectAndDontRequeueException error = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(message(event())));

        assertInstanceOf(NonRetryableDeliveryException.class, error.getCause());
        verify(failures).recordFailure("order-1", "identity mismatch");
    }

    @Test
    void malformedMessageWithoutOrderIdIsRejectedAndAcknowledgedByDeadLetterRoute() {
        AwardDeliveryApplicationService service = mock(AwardDeliveryApplicationService.class);
        DeliveryFailureRecorder failures = mock(DeliveryFailureRecorder.class);
        AwardDeliveryConsumer consumer = consumer(service, failures);
        Message malformed = MessageBuilder.withBody("not-json".getBytes()).build();

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(malformed));

        verifyNoInteractions(service, failures);
    }

    @Test
    void failureRecorderTruncatesReasonAndOnlyDelegatesConditionalUpdate() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        DeliveryFailureRecorder recorder = new DeliveryFailureRecorder(tasks);

        recorder.recordFailure("order-1", "x".repeat(600));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(tasks).recordConsumerFailure(eq("order-1"), reason.capture());
        assertEquals(500, reason.getValue().length());
    }

    @Test
    void retryTemplateAttemptsTransientFailureFiveTimesButPermanentFailureOnce() {
        RetryTemplate retry = RabbitMQConfig.createDeliveryRetryTemplate(1L, 4L);
        AtomicInteger transientAttempts = new AtomicInteger();

        assertThrows(RetryableDeliveryException.class, () -> retry.execute(context -> {
            transientAttempts.incrementAndGet();
            throw new RetryableDeliveryException("temporary");
        }));
        assertEquals(5, transientAttempts.get());

        AtomicInteger permanentAttempts = new AtomicInteger();
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> retry.execute(context -> {
            permanentAttempts.incrementAndGet();
            throw new AmqpRejectAndDontRequeueException(
                    "permanent", new NonRetryableDeliveryException("invalid"));
        }));
        assertEquals(1, permanentAttempts.get());
    }

    private static AwardDeliveryConsumer consumer(
            AwardDeliveryApplicationService service, DeliveryFailureRecorder failures) {
        return new AwardDeliveryConsumer(service, failures,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static Message message(
            DomainEventEnvelope<AwardDeliveryRequestedPayload> event) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return MessageBuilder.withBody(mapper.writeValueAsBytes(event)).build();
    }

    private static DomainEventEnvelope<AwardDeliveryRequestedPayload> event() {
        return new DomainEventEnvelope<>("event-1", "AWARD_DELIVERY_REQUESTED",
                "award-delivery:order-1:v0", 1, "order-1", "user-1",
                Instant.parse("2026-09-28T12:00:00Z"),
                new AwardDeliveryRequestedPayload("order-1", "user-1"));
    }
}
