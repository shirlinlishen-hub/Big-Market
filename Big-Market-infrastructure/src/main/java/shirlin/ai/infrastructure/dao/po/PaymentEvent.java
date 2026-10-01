package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

@Data
public class PaymentEvent {
    private String eventId;
    private String eventType;
    private String paymentOrderNo;
    private String userId;
    private Long activityId;
    private Long skuId;
    private String payloadHash;
    private Integer status;
    private String purchaseOrderId;
    private Integer removedUnusedCount;
    private Integer consumedExposureCount;
}
