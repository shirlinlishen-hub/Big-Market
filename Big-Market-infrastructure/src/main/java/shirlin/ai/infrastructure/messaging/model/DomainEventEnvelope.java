package shirlin.ai.infrastructure.messaging.model;

import java.time.Instant;

public record DomainEventEnvelope<T>(
        String eventId,
        String eventType,
        String eventKey,
        int schemaVersion,
        String aggregateId,
        String partitionKey,
        Instant occurredAt,
        T payload) {
}
