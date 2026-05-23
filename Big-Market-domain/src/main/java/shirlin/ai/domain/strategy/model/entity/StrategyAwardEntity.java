package shirlin.ai.domain.strategy.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyAwardEntity {

    private Long strategyId;

    private Integer awardId;

    private Integer awardType;

    private Integer awardCount;

    private Integer awardSurplus;

    private BigDecimal awardRate;

    private Integer sort;

    private String ruleModels;

}
