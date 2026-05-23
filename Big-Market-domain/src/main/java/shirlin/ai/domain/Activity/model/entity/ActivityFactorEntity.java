package shirlin.ai.domain.Activity.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 抽奖因子，入参
 */
public class RaffleFactorEntity {

    private String userId;
    private String activityId;
    private Long strategyId;
}
