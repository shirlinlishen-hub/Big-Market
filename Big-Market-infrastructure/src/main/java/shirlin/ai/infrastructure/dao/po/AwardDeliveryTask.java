package shirlin.ai.infrastructure.dao.po;

import lombok.Data;
import java.util.Date;

@Data
public class AwardDeliveryTask {
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
