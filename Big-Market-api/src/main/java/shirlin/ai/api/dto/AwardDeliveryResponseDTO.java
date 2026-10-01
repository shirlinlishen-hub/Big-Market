package shirlin.ai.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class AwardDeliveryResponseDTO {
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
}
