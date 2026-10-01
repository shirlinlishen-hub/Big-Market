package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.DrawOrder;

import java.util.Date;
import java.util.List;

@Mapper
public interface IDrawOrderDao {
    DrawOrder selectByUserIdAndRequestNo(@Param("userId") String userId,
                                          @Param("requestNo") String requestNo);
    DrawOrder selectForUpdate(@Param("userId") String userId, @Param("orderId") String orderId);
    int insert(DrawOrder order);
    int complete(@Param("userId") String userId, @Param("orderId") String orderId,
                 @Param("awardId") Integer awardId, @Param("awardType") Integer awardType);
    List<DrawOrder> selectTimedOut(@Param("before") Date before);
    int cancel(@Param("userId") String userId, @Param("orderId") String orderId);
}
