package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

@Data
public class ActivityCount {
    private Long activityCountId;
    private Integer totalCount;
    private Integer monthCount;
    private Integer dayCount;
}
