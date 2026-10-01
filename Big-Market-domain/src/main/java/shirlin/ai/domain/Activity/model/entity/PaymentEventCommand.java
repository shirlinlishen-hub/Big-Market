package shirlin.ai.domain.Activity.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentEventCommand {
    private String eventId;
    private String eventType;
    private String paymentOrderNo;
    private String userId;
    private Long activityId;
    private Long skuId;
    private String payloadHash;
}
