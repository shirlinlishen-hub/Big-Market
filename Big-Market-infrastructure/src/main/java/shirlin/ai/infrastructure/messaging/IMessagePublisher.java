package shirlin.ai.infrastructure.messaging;

import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

public interface IMessagePublisher {
    void publish(DomainEventEnvelope<AwardDeliveryRequestedPayload> event);
}
