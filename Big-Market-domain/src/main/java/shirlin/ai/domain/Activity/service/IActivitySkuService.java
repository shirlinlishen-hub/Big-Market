package shirlin.ai.domain.Activity.service;

import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;

public interface IActivitySkuService {

    /**
     * 创建参与订单
     * 输入: 用户ID + SKU ID
     * 输出: 订单实体 (包含 strategyId, 但 activity 域不使用它)
     */
    ActivityOrderEntity createOrder(ActivityFactorEntity factor);
}
