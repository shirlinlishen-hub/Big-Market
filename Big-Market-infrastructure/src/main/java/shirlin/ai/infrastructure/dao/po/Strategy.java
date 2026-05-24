package shirlin.ai.infrastructure.dao.po;

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
public class Strategy {

    private Long id;
    private Long strategyId;
    private String strategyName;
    private String strategyDesc;
    private BigDecimal totalProbability;
    private Integer probabilityPrecision;
    private Integer freeDrawCount;
    private Integer pointsPerDraw;
    private Integer status;
    private Date beginTime;
    private Date endTime;
    private Date createTime;
    private Date updateTime;
}