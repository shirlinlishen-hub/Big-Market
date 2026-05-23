package shirlin.ai.domain.strategy.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AwardRateRange {

    private Integer awardId;
    private int rangeStart;
    private int rangeEnd;

}
