package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import java.util.List;

@Mapper
public interface IAwardDeliveryTaskDao {
    int insert(AwardDeliveryTask task);
    List<AwardDeliveryTask> selectDueTasks();
    AwardDeliveryTask selectForUpdate(@Param("userId") String userId, @Param("orderId") String orderId);
    int claim(@Param("orderId") String orderId);
    int markSuccess(@Param("orderId") String orderId);
    int markManual(@Param("orderId") String orderId, @Param("reason") String reason);
    int markRetry(@Param("orderId") String orderId, @Param("reason") String reason);
    int recordFailure(@Param("userId") String userId, @Param("orderId") String orderId,
                      @Param("reason") String reason);
    int recordConsumerFailure(@Param("orderId") String orderId,
                              @Param("reason") String reason);
    int markFailedFromDeadLetter(@Param("orderId") String orderId,
                                 @Param("reason") String reason);
    AwardDeliveryTask selectByUserAndOrder(@Param("userId") String userId,
                                           @Param("orderId") String orderId);
    AwardDeliveryTask selectByOrderIdForUpdate(@Param("orderId") String orderId);
    List<AwardDeliveryTask> selectByStatus(@Param("status") Integer status,
                                          @Param("limit") Integer limit);
    int requeue(@Param("orderId") String orderId);
    int markManualSuccess(@Param("orderId") String orderId);
    int markManualFailed(@Param("orderId") String orderId, @Param("reason") String reason);
}
