package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IAwardDeliveryAuditDao {
    int insert(@Param("orderId") String orderId, @Param("operatorId") String operatorId,
               @Param("action") String action, @Param("note") String note);
}
