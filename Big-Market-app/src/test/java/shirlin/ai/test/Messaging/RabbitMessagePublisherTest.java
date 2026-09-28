package shirlin.ai.test.Messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.messaging.exception.MessagePublishException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;
import shirlin.ai.messaging.RabbitMessagePublisher;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitMessagePublisherTest {

    @Test
    void publishesPersistentJsonWithBusinessIdentifiersAfterAck() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq(RabbitMQConfig.EVENT_EXCHANGE),
                eq(RabbitMQConfig.DELIVERY_ROUTE), any(Message.class), any(CorrelationData.class));
        RabbitMessagePublisher publisher = publisher(template, Duration.ofSeconds(1));

        publisher.publish(event());

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).send(eq(RabbitMQConfig.EVENT_EXCHANGE),
                eq(RabbitMQConfig.DELIVERY_ROUTE), message.capture(), correlation.capture());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getValue().getMessageProperties().getDeliveryMode());
        assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
        assertEquals("event-1", message.getValue().getMessageProperties().getMessageId());
        assertEquals("order-1", message.getValue().getMessageProperties().getCorrelationId());
        assertEquals("event-1", correlation.getValue().getId());
        String json = new String(message.getValue().getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"eventType\":\"AWARD_DELIVERY_REQUESTED\""));
        assertTrue(json.contains("\"orderId\":\"order-1\""));
    }

    @Test
    void rejectsNack() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker unavailable"));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        MessagePublishException error = assertThrows(MessagePublishException.class,
                () -> publisher(template, Duration.ofSeconds(1)).publish(event()));

        assertTrue(error.getMessage().contains("broker unavailable"));
    }

    @Test
    void rejectsReturnedUnroutableMessageEvenWhenBrokerAcks() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message sent = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(sent, 312, "NO_ROUTE",
                    RabbitMQConfig.EVENT_EXCHANGE, RabbitMQConfig.DELIVERY_ROUTE));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        MessagePublishException error = assertThrows(MessagePublishException.class,
                () -> publisher(template, Duration.ofSeconds(1)).publish(event()));

        assertTrue(error.getMessage().contains("NO_ROUTE"));
    }

    @Test
    void rejectsConfirmTimeout() {
        RabbitTemplate template = mock(RabbitTemplate.class);

        MessagePublishException error = assertThrows(MessagePublishException.class,
                () -> publisher(template, Duration.ofMillis(1)).publish(event()));

        assertTrue(error.getMessage().contains("timed out"));
    }

    @Test
    void declaresDurableDeliveryQueueWithDeadLetterRoute() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue delivery = config.deliveryQueue();
        Queue dead = config.deadQueue();

        assertTrue(delivery.isDurable());
        assertEquals(RabbitMQConfig.DEAD_EXCHANGE,
                delivery.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.DEAD_ROUTE,
                delivery.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(dead.isDurable());
        assertEquals(RabbitMQConfig.DELIVERY_QUEUE, delivery.getName());
        assertEquals(RabbitMQConfig.DEAD_QUEUE, dead.getName());
    }

    private static RabbitMessagePublisher publisher(RabbitTemplate template, Duration timeout) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new RabbitMessagePublisher(template, objectMapper, timeout);
    }

    private static DomainEventEnvelope<AwardDeliveryRequestedPayload> event() {
        return new DomainEventEnvelope<>("event-1", "AWARD_DELIVERY_REQUESTED",
                "award-delivery:order-1:v0", 1, "order-1", "user-1",
                Instant.parse("2026-09-28T12:00:00Z"),
                new AwardDeliveryRequestedPayload("order-1", "user-1"));
    }
}
