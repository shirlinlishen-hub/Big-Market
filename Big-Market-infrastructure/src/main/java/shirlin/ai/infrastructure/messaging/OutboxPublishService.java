package shirlin.ai.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

import java.time.Duration;
import java.util.Date;

@Service
public class OutboxPublishService {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxClaimService claimService;
    private final IMessagePublisher messagePublisher;
    private final IOutboxEventDao eventDao;
    private final ObjectMapper objectMapper;

    public OutboxPublishService(OutboxClaimService claimService,
                                IMessagePublisher messagePublisher,
                                IOutboxEventDao eventDao,
                                ObjectMapper objectMapper) {
        this.claimService = claimService;
        this.messagePublisher = messagePublisher;
        this.eventDao = eventDao;
        this.objectMapper = objectMapper;
    }

    public int publishBatch(String workerId, int batchSize, Duration lease) {
        int published = 0;
        for (OutboxEvent event : claimService.claimBatch(workerId, batchSize, lease)) {
            try {
                messagePublisher.publish(toEnvelope(event));
                int updated = eventDao.markPublished(event.getEventId(), event.getLockToken());
                if (updated == 1) {
                    published++;
                } else {
                    log.warn("Outbox publish confirmed but lease token is stale, eventId={}",
                            event.getEventId());
                }
            } catch (Exception e) {
                recordFailure(event, e);
            }
        }
        return published;
    }

    private DomainEventEnvelope<AwardDeliveryRequestedPayload> toEnvelope(
            OutboxEvent event) throws Exception {
        Date createTime = event.getCreateTime();
        if (createTime == null) {
            throw new IllegalArgumentException("Outbox create time is required");
        }
        AwardDeliveryRequestedPayload payload = objectMapper.readValue(
                event.getPayload(), AwardDeliveryRequestedPayload.class);
        return new DomainEventEnvelope<>(
                event.getEventId(),
                event.getEventType(),
                event.getEventKey(),
                1,
                event.getAggregateId(),
                event.getPartitionKey(),
                createTime.toInstant(),
                payload);
    }

    private void recordFailure(OutboxEvent event, Exception failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
        reason = reason.substring(0, Math.min(reason.length(), MAX_ERROR_LENGTH));
        try {
            eventDao.recordPublishFailure(event.getEventId(), event.getLockToken(), reason);
        } catch (Exception recordError) {
            log.error("Cannot record Outbox publish failure, eventId={}",
                    event.getEventId(), recordError);
        }
        log.warn("Outbox publish failed, eventId={}, reason={}",
                event.getEventId(), reason);
    }
}
