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
public class StrategyEntity {

    private Long strategyId;

    private BigDecimal totalProbability;

    private Integer probabilityPrecision;

    private Integer freeDrawCount;

    private Integer pointsPerDraw;

    private Integer status;

    private Date beginTime;

    private Date endTime;

}
