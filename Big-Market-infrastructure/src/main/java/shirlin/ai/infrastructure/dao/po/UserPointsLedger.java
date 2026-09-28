package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

@Data
public class UserPointsLedger {
    private String orderId;
    private String userId;
    private Integer points;
    private Date createTime;
}
