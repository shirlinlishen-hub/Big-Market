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
public class Activity {

    private Long id;
    private Long activityId;
    private String activityName;
    private String activityDesc;
    private Long strategyId;
    private Integer status;
    private Date beginTime;
    private Date endTime;
    private Date createTime;
    private Date updateTime;
}
