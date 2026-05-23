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
public class ActivitySku {

    private Long id;
    private Long skuId;
    private Long activityId;
    private String skuName;
    private Integer skuType;
    private Integer pointsCost;
    private Long activityCountId;
    private Integer stockCount;
    private Integer stockSurplus;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
