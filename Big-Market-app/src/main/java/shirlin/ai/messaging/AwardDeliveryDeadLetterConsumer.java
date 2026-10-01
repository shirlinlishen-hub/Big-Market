package shirlin.ai.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;

@Component
public class AwardDeliveryDeadLetterConsumer {
    private static final Logger log =
            LoggerFactory.getLogger(AwardDeliveryDeadLetterConsumer.class);
    private static final int MAX_REASON_LENGTH = 500;
    private static final TypeReference<DomainEventEnvelope<AwardDeliveryRequestedPayload>>
            EVENT_TYPE = new TypeReference<>() { };

    private final IAwardDeliveryTaskDao taskDao;
    private final IAwardDeliveryAuditDao auditDao;
    private final ObjectMapper objectMapper;

    public AwardDeliveryDeadLetterConsumer(IAwardDeliveryTaskDao taskDao,
                                           IAwardDeliveryAuditDao auditDao,
                                           ObjectMapper objectMapper) {
        this.taskDao = taskDao;
        this.auditDao = auditDao;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.DEAD_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void consume(Message message) {
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event;
        try {
            event = objectMapper.readValue(message.getBody(), EVENT_TYPE);
        } catch (Exception e) {
            log.error("Cannot parse award delivery dead letter, messageId={}",
                    message.getMessageProperties().getMessageId(), e);
            return;
        }
        if (event.payload() == null || event.payload().orderId() == null
                || event.payload().orderId().isBlank()) {
            log.error("Award delivery dead letter has no orderId, messageId={}",
                    message.getMessageProperties().getMessageId());
            return;
        }

        String orderId = event.payload().orderId();
        String reason = deadLetterReason(message);
        if (taskDao.markFailedFromDeadLetter(orderId, reason) == 1
                && auditDao.insert(orderId, "rabbitmq", "MQ_DEAD_LETTER", reason) != 1) {
            throw new IllegalStateException("Dead-letter audit was not recorded");
        }
    }

    private String deadLetterReason(Message message) {
        Object exceptionMessage = message.getMessageProperties()
                .getHeaders().get("x-exception-message");
        Object death = message.getMessageProperties().getHeaders().get("x-death");
        String reason = exceptionMessage != null
                ? exceptionMessage.toString()
                : death != null ? death.toString() : "RabbitMQ delivery retries exhausted";
        return reason.substring(0, Math.min(reason.length(), MAX_REASON_LENGTH));
    }
}
