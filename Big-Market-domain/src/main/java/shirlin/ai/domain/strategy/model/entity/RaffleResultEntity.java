package shirlin.ai.domain.strategy.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 抽奖结果 出参
 */
public class RaffleResultEntity {

    private Integer awardId;
    private String awardTitle;
    private Integer awardType;
    private Integer sort;

}
