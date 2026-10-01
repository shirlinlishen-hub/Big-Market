package shirlin.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.po.OutboxEvent;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;

import java.util.Map;

@Service
public class TransactionalOutboxService {
    @Resource private IOutboxEventDao eventDao;
    @Resource private SnowflakeIdGenerator idGenerator;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, String eventKey, String aggregateId,
                       String partitionKey, Map<String, ?> payload) {
        if (blank(eventType) || blank(eventKey) || blank(aggregateId) || blank(partitionKey)
                || payload == null) {
            throw new IllegalArgumentException("Outbox event fields are required");
        }
        OutboxEvent event = new OutboxEvent();
        event.setEventId(String.valueOf(idGenerator.nextId()));
        event.setEventType(eventType); event.setEventKey(eventKey);
        event.setAggregateId(aggregateId); event.setPartitionKey(partitionKey);
        event.setPayload(JSON.toJSONString(payload)); event.setStatus(0);
        eventDao.insertIdempotent(event);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
