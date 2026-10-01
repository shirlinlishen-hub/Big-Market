package shirlin.ai.domain.Activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityOrderEntity {

    private String orderId;
    private String userId;
    private Long activityId;
    private Long skuId;
    private Long strategyId;
    private Integer orderStatus;
    private Integer pointsCost;
    private String outBusinessNo;
    private Integer grantTotalCount;
    private Integer grantMonthCount;
    private Integer grantDayCount;
    private Integer refundRemovedCount;
    private Integer refundExposureCount;

}
