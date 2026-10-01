package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.domain.Activity.model.entity.ActivityOrderEntity;
import shirlin.ai.infrastructure.dao.po.ActivityOrder;

import java.util.List;

@Mapper
public interface IActivityOrderDao {

    int insert(ActivityOrder activityOrderPO);

    int updateById(ActivityOrder activityOrderPO);

    int deleteById(Long id);

    ActivityOrder selectById(Long id);

    ActivityOrder selectByOrderId(String orderId);

    ActivityOrder selectByOutBusinessNo(String outBusinessNo);

    ActivityOrder selectByUserIdAndOutBusinessNo(@Param("userId") String userId,
                                                   @Param("outBusinessNo") String outBusinessNo);
    ActivityOrder selectByUserIdAndOutBusinessNoForUpdate(@Param("userId") String userId,
                                                           @Param("outBusinessNo") String outBusinessNo);

    List<ActivityOrder> selectByUserIdAndActivityId(@Param("userId") String userId, @Param("activityId") Long activityId);

    int updateOrderStatus(@Param("orderId") String orderId, @Param("orderStatus") Integer orderStatus);

    ActivityOrderEntity queryUnusedOrder(String userId, Long skuId);

    int countGrantedOrders(@Param("userId") String userId, @Param("activityId") Long activityId);
    int markRefunded(@Param("orderId") String orderId, @Param("refundEventId") String refundEventId,
                     @Param("status") Integer status, @Param("removedCount") Integer removedCount,
                     @Param("exposureCount") Integer exposureCount);
    Integer selectMaxActiveMonthCap(@Param("userId") String userId, @Param("activityId") Long activityId);
    Integer selectMaxActiveDayCap(@Param("userId") String userId, @Param("activityId") Long activityId);
}
