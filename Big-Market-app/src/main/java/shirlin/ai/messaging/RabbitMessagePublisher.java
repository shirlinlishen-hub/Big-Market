package shirlin.ai.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.messaging.IMessagePublisher;
import shirlin.ai.infrastructure.messaging.exception.MessagePublishException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitMessagePublisher implements IMessagePublisher {
    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    @Autowired
    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this(rabbitTemplate, objectMapper, DEFAULT_CONFIRM_TIMEOUT);
    }

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper,
                                  Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(DomainEventEnvelope<AwardDeliveryRequestedPayload> event) {
        CorrelationData correlation = new CorrelationData(event.eventId());
        Message message = message(event);
        rabbitTemplate.send(RabbitMQConfig.EVENT_EXCHANGE,
                RabbitMQConfig.DELIVERY_ROUTE, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new MessagePublishException("RabbitMQ rejected event "
                        + event.eventId() + ": " + confirm.getReason());
            }
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                throw new MessagePublishException("RabbitMQ returned event "
                        + event.eventId() + ": " + returned.getReplyText());
            }
        } catch (TimeoutException e) {
            throw new MessagePublishException(
                    "RabbitMQ confirm timed out for event " + event.eventId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException(
                    "Interrupted while waiting for RabbitMQ confirm for event "
                            + event.eventId(), e);
        } catch (ExecutionException e) {
            throw new MessagePublishException(
                    "RabbitMQ confirm failed for event " + event.eventId(), e.getCause());
        }
    }

    private Message message(DomainEventEnvelope<AwardDeliveryRequestedPayload> event) {
        try {
            return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType("application/json")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.eventId())
                    .setCorrelationId(event.aggregateId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new MessagePublishException(
                    "Cannot serialize event " + event.eventId(), e);
        }
    }
}
