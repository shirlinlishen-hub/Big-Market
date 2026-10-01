package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.PaymentEvent;

@Mapper
public interface IPaymentEventDao {
    int insertIgnore(PaymentEvent event);
    PaymentEvent selectByEventId(@Param("eventId") String eventId);
    int markResult(@Param("eventId") String eventId, @Param("status") Integer status,
                   @Param("purchaseOrderId") String purchaseOrderId,
                   @Param("removedUnusedCount") Integer removedUnusedCount,
                   @Param("consumedExposureCount") Integer consumedExposureCount);
}
