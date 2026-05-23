package shirlin.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityOrder {

    private Long id;
    private String orderId;
    private String userId;
    private Long activityId;
    private Long skuId;
    private Long strategyId;
    private Integer orderStatus;
    private Integer pointsCost;
    private String outBusinessNo;
    private Date createTime;
    private Date updateTime;
}
