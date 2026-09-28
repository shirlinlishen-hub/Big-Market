package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

@Data
public class OutboxEvent {
    private String eventId;
    private String eventType;
    private String eventKey;
    private String aggregateId;
    private String partitionKey;
    private String payload;
    private Integer status;
    private Integer attempts;
    private Date nextAttemptAt;
    private String lastError;
    private Date createTime;
    private String lockedBy;
    private String lockToken;
    private Date lockedUntil;
    private Date publishedTime;
    private Date deadTime;
}
