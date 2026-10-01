package shirlin.ai.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentEventResponseDTO {
    private String eventId;
    private String status;
    private String purchaseOrderId;
    private Integer removedUnusedCount;
    private Integer consumedExposureCount;
}
