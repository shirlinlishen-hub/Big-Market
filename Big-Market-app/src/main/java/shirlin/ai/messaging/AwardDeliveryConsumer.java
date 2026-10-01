package shirlin.ai.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.delivery.AwardDeliveryApplicationService;
import shirlin.ai.infrastructure.delivery.DeliveryFailureRecorder;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

@Component
@ConditionalOnProperty(prefix = "big-market.delivery", name = "mode", havingValue = "mq")
public class AwardDeliveryConsumer {
    private static final TypeReference<DomainEventEnvelope<AwardDeliveryRequestedPayload>>
            EVENT_TYPE = new TypeReference<>() { };

    private final AwardDeliveryApplicationService deliveryService;
    private final DeliveryFailureRecorder failureRecorder;
    private final ObjectMapper objectMapper;

    public AwardDeliveryConsumer(AwardDeliveryApplicationService deliveryService,
                                 DeliveryFailureRecorder failureRecorder,
                                 ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.failureRecorder = failureRecorder;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.DELIVERY_QUEUE,
            containerFactory = "awardDeliveryListenerContainerFactory")
    public void consume(Message message) {
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event = parse(message);
        String orderId = event.payload() == null ? null : event.payload().orderId();
        try {
            deliveryService.deliver(event);
        } catch (NonRetryableDeliveryException e) {
            failureRecorder.recordFailure(orderId, reason(e));
            throw new AmqpRejectAndDontRequeueException(
                    "Permanent award delivery failure", e);
        } catch (RuntimeException e) {
            failureRecorder.recordFailure(orderId, reason(e));
            throw e;
        }
    }

    private DomainEventEnvelope<AwardDeliveryRequestedPayload> parse(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EVENT_TYPE);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException(
                    "Malformed award delivery message", e);
        }
    }

    private String reason(RuntimeException failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
