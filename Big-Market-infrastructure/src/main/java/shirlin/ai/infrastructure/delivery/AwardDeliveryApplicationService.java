package shirlin.ai.infrastructure.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IInboxEventDao;
import shirlin.ai.infrastructure.dao.po.InboxEvent;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AwardDeliveryApplicationService {
    public static final String CONSUMER_NAME = "award-delivery-consumer-v1";
    private static final String EVENT_TYPE = "AWARD_DELIVERY_REQUESTED";

    private final IInboxEventDao inboxDao;
    private final AwardFulfillmentService fulfillmentService;
    private final ObjectMapper objectMapper;

    public AwardDeliveryApplicationService(IInboxEventDao inboxDao,
                                           AwardFulfillmentService fulfillmentService,
                                           ObjectMapper objectMapper) {
        this.inboxDao = inboxDao;
        this.fulfillmentService = fulfillmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryResult deliver(
            DomainEventEnvelope<AwardDeliveryRequestedPayload> event) {
        validate(event);
        String payloadHash = payloadHash(event.payload());
        InboxEvent inserted = inbox(event, payloadHash);
        if (inboxDao.insertOrTouch(inserted) <= 0) {
            throw new RetryableDeliveryException("Inbox event was not registered");
        }
        InboxEvent stored = inboxDao.selectForUpdate(CONSUMER_NAME, event.eventId());
        if (stored == null) {
            throw new RetryableDeliveryException("Inbox event disappeared after registration");
        }
        if (!sameIdentity(stored, event, payloadHash)) {
            throw new NonRetryableDeliveryException(
                    "Event ID conflicts with an existing Inbox message");
        }
        if (stored.getStatus() != null && stored.getStatus() == 1) {
            return DeliveryResult.DUPLICATE;
        }
        if (stored.getStatus() == null || stored.getStatus() != 0) {
            throw new NonRetryableDeliveryException("Inbox status is invalid");
        }

        fulfillmentService.fulfill(event.payload().userId(), event.payload().orderId());
        if (inboxDao.markSuccess(CONSUMER_NAME, event.eventId()) != 1) {
            throw new RetryableDeliveryException("Inbox success state changed concurrently");
        }
        return DeliveryResult.PROCESSED;
    }

    private void validate(DomainEventEnvelope<AwardDeliveryRequestedPayload> event) {
        if (event == null || blank(event.eventId()) || blank(event.eventKey())
                || blank(event.aggregateId()) || blank(event.partitionKey())
                || event.occurredAt() == null || event.payload() == null) {
            throw new NonRetryableDeliveryException("Delivery event fields are required");
        }
        if (!EVENT_TYPE.equals(event.eventType()) || event.schemaVersion() != 1) {
            throw new NonRetryableDeliveryException("Delivery event type or schema is unsupported");
        }
        AwardDeliveryRequestedPayload payload = event.payload();
        if (blank(payload.orderId()) || blank(payload.userId())
                || !event.aggregateId().equals(payload.orderId())
                || !event.partitionKey().equals(payload.userId())) {
            throw new NonRetryableDeliveryException("Delivery event identity is inconsistent");
        }
        String prefix = "award-delivery:" + payload.orderId() + ":v";
        String version = event.eventKey().startsWith(prefix)
                ? event.eventKey().substring(prefix.length()) : "";
        if (version.isEmpty() || !version.chars().allMatch(Character::isDigit)) {
            throw new NonRetryableDeliveryException("Delivery event key is inconsistent");
        }
    }

    private InboxEvent inbox(
            DomainEventEnvelope<AwardDeliveryRequestedPayload> event,
            String payloadHash) {
        InboxEvent inbox = new InboxEvent();
        inbox.setConsumerName(CONSUMER_NAME);
        inbox.setEventId(event.eventId());
        inbox.setEventKey(event.eventKey());
        inbox.setAggregateId(event.aggregateId());
        inbox.setPayloadHash(payloadHash);
        inbox.setStatus(0);
        return inbox;
    }

    private boolean sameIdentity(InboxEvent stored,
                                 DomainEventEnvelope<AwardDeliveryRequestedPayload> event,
                                 String payloadHash) {
        return event.eventKey().equals(stored.getEventKey())
                && event.aggregateId().equals(stored.getAggregateId())
                && payloadHash.equals(stored.getPayloadHash());
    }

    private String payloadHash(AwardDeliveryRequestedPayload payload) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new NonRetryableDeliveryException("Cannot calculate delivery payload hash", e);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
