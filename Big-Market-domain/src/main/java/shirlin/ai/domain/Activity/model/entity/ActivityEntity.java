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
public class ActivityEntity {

    private Long activityId;

    private Long strategyId;

    private Integer status;

    private Date beginTime;

    private Date endTime;

}
