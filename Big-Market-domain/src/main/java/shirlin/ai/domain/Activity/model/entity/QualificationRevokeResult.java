package shirlin.ai.domain.Activity.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualificationRevokeResult {
    private String purchaseOrderId;
    private boolean manualReview;
    private int removedUnusedCount;
    private int consumedExposureCount;
}
