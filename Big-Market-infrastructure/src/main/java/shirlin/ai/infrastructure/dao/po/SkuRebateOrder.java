package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

@Data
public class SkuRebateOrder {
    private String purchaseOrderId;
    private String userId;
    private Long activityId;
    private Integer rebateDrawCount;
    private Integer status;
}
