package shirlin.ai.api.dto;

import lombok.Data;

@Data
public class PaymentEventRequestDTO {
    private String eventId;
    private String eventType;
    private String paymentOrderNo;
    private String userId;
    private Long activityId;
    private Long skuId;
}
