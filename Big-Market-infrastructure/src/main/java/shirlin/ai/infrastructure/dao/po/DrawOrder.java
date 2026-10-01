package shirlin.ai.infrastructure.dao.po;

import lombok.Data;
import java.util.Date;

@Data
public class DrawOrder {
    private String orderId;
    private String userId;
    private Long activityId;
    private Long strategyId;
    private String requestNo;
    private Integer status;
    private Integer awardId;
    private Integer awardType;
    private Date drawDay;
    private String drawMonth;
    private Date createTime;
}
