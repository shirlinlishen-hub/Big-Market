package shirlin.ai.test.Messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;
import shirlin.ai.messaging.AwardDeliveryDeadLetterConsumer;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AwardDeliveryDeadLetterConsumerTest {

    @Test
    void pendingOrProcessingTaskBecomesFailedWithOneAuditRecord() throws Exception {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IAwardDeliveryAuditDao audits = mock(IAwardDeliveryAuditDao.class);
        when(tasks.markFailedFromDeadLetter(eq("order-1"), anyString())).thenReturn(1);
        when(audits.insert(eq("order-1"), eq("rabbitmq"),
                eq("MQ_DEAD_LETTER"), anyString())).thenReturn(1);
        AwardDeliveryDeadLetterConsumer consumer = consumer(tasks, audits);

        consumer.consume(message());

        verify(tasks).markFailedFromDeadLetter(eq("order-1"), anyString());
        verify(audits).insert(eq("order-1"), eq("rabbitmq"),
                eq("MQ_DEAD_LETTER"), anyString());
    }

    @Test
    void successfulManualOrRepeatedDeadLetterCannotOverwriteStateOrDuplicateAudit()
            throws Exception {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IAwardDeliveryAuditDao audits = mock(IAwardDeliveryAuditDao.class);
        when(tasks.markFailedFromDeadLetter(eq("order-1"), anyString()))
                .thenReturn(1, 0, 0);
        when(audits.insert(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        AwardDeliveryDeadLetterConsumer consumer = consumer(tasks, audits);

        consumer.consume(message());
        consumer.consume(message());
        consumer.consume(message());

        verify(audits, times(1)).insert(eq("order-1"), eq("rabbitmq"),
                eq("MQ_DEAD_LETTER"), anyString());
    }

    @Test
    void poisonDeadLetterWithoutOrderIdIsLoggedAndAcked() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IAwardDeliveryAuditDao audits = mock(IAwardDeliveryAuditDao.class);
        AwardDeliveryDeadLetterConsumer consumer = consumer(tasks, audits);
        Message poison = MessageBuilder.withBody("not-json".getBytes())
                .setMessageId("poison-1").build();

        assertDoesNotThrow(() -> consumer.consume(poison));

        verifyNoInteractions(tasks, audits);
    }

    private static AwardDeliveryDeadLetterConsumer consumer(
            IAwardDeliveryTaskDao tasks, IAwardDeliveryAuditDao audits) {
        return new AwardDeliveryDeadLetterConsumer(tasks, audits,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private static Message message() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event =
                new DomainEventEnvelope<>("event-1", "AWARD_DELIVERY_REQUESTED",
                        "award-delivery:order-1:v0", 1, "order-1", "user-1",
                        Instant.parse("2026-09-28T12:00:00Z"),
                        new AwardDeliveryRequestedPayload("order-1", "user-1"));
        return MessageBuilder.withBody(mapper.writeValueAsBytes(event))
                .setMessageId("event-1")
                .setHeader("x-death", java.util.List.of(java.util.Map.of(
                        "reason", "rejected", "count", 1L)))
                .build();
    }
}
