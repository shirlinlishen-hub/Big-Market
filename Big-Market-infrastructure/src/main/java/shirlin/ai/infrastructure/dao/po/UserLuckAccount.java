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
public class UserLuckAccount {
    private Long id;
    private String userId;
    private Long strategyId;
    private Integer luckValue;
    private Date createTime;
    private Date updateTime;
}