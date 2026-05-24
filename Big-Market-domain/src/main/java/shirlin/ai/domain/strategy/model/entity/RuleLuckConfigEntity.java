package shirlin.ai.domain.strategy.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleLuckConfigEntity {

    /** 对应 DB JSON luck_threshold */
    private int luckThreshold;

    /** 对应 DB JSON luck_award_id */
    private int luckAwardId;
}
