package shirlin.ai.domain.Activity.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentEventResult {
    private String eventId;
    private String status;
    private String purchaseOrderId;
    private Integer removedUnusedCount;
    private Integer consumedExposureCount;
}
