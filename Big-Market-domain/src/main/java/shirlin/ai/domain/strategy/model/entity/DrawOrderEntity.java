package shirlin.ai.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawOrderEntity {
    private String orderId;
    private String userId;
    private Long activityId;
    private Long strategyId;
    private String requestNo;
    /** 0 = processing, 1 = completed, 2 = cancelled. */
    private Integer status;
    private Integer awardId;
    private Integer awardType;
    /** Transient response marker; it is never persisted. */
    private boolean newlyCreated;
}
