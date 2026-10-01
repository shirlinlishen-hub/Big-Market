package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;

import java.util.List;

@Mapper
public interface ISkuRebateDao {
    Integer selectConfiguredCount(@Param("skuId") Long skuId);
    int insertOrder(SkuRebateOrder order);
    List<SkuRebateOrder> selectPending();
    SkuRebateOrder selectForUpdate(@Param("purchaseOrderId") String purchaseOrderId);
    int complete(@Param("purchaseOrderId") String purchaseOrderId);
    int cancel(@Param("purchaseOrderId") String purchaseOrderId);
}
