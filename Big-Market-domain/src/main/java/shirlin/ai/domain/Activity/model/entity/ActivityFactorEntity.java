package shirlin.ai.domain.Activity.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 活动参与因子，入参
 */
public class ActivityFactorEntity {

    private String userId;
    private Long activityId;
    private Long strategyId;
    private Long skuId;
    /** Stable caller-supplied purchase or draw request id for idempotency. */
    private String outBusinessNo;
}
