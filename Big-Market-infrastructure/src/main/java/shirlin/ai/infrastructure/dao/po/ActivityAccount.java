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
public class ActivityAccount {

    private Long id;
    private String userId;
    private Long activityId;
    private Integer totalCount;
    private Integer totalCountSurplus;
    private Integer monthCount;
    private Integer monthCountSurplus;
    private Integer dayCount;
    private Integer dayCountSurplus;
    private Date createTime;
    private Date updateTime;
}
