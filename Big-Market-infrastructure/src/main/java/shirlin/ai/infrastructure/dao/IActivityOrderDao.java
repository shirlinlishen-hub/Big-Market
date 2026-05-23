package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    List<ActivityOrder> selectByUserIdAndActivityId(@Param("userId") String userId, @Param("activityId") Long activityId);

    int updateOrderStatus(@Param("orderId") String orderId, @Param("orderStatus") Integer orderStatus);
}
