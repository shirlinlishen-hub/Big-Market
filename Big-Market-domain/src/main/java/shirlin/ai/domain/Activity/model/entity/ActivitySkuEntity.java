package shirlin.ai.domain.Activity.model.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySkuEntity {

    private Long skuId;

    private Long activityId;

    private Integer skuType;

    //消耗积分数量
    private Integer pointsCost;

    //额度配置 总额度/月额度/日额度
    private Long activityCountId;

    //SKU总库存
    private Long stockCount;

    //SKU剩余库存
    private Long stockSurplus;

    //SKU状态
    private Integer status;

}
