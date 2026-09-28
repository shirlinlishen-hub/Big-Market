package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

@Data
public class InboxEvent {
    private String consumerName;
    private String eventId;
    private String eventKey;
    private String aggregateId;
    private String payloadHash;
    private Integer status;
    private Integer duplicateCount;
    private Date firstReceivedTime;
    private Date lastReceivedTime;
    private Date processedTime;
}
