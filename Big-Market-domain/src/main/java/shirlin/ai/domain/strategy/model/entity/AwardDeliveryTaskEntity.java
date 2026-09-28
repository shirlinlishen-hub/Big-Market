package shirlin.ai.domain.strategy.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class AwardDeliveryTaskEntity {
    private String orderId;
    private String userId;
    private Integer awardId;
    private Integer awardType;
    private String awardKey;
    private String awardValue;
    private Integer status;
    private Integer attempts;
    private Date nextRetryAt;
    private String lastError;
    private Integer dispatchVersion;
}
