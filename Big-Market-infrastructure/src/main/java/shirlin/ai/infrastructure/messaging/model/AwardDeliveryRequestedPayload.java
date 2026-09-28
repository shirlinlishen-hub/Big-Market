package shirlin.ai.infrastructure.messaging.model;

public record AwardDeliveryRequestedPayload(String orderId, String userId) {
}
