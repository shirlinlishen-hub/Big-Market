package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.UserAwardRecord;

import java.util.Date;
import java.util.List;

@Mapper
public interface IUserAwardRecordDao {
    int insert(UserAwardRecord userAwardRecordPO);

    int updateById(UserAwardRecord userAwardRecordPO);

    int deleteById(Long id);

    UserAwardRecord selectById(Long id);

    List<UserAwardRecord> selectByUserIdAndStrategyId(@Param("userId") String userId, @Param("strategyId") Long strategyId);

    List<UserAwardRecord> selectByUserId(@Param("userId") String userId);

    int updateAwardState(@Param("id") Long id, @Param("awardState") Integer awardState);
    int updateAwardStateByOrder(@Param("userId") String userId,
                                @Param("orderId") String orderId,
                                @Param("awardState") Integer awardState);

    List<UserAwardRecord> selectByAwardState(@Param("awardState") Integer awardState);

    List<UserAwardRecord> selectByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    int countByUserIdAndStrategyId(@Param("userId") String userId, @Param("strategyId") Long strategyId);

    int insertOrder(UserAwardRecord userAwardRecordPO);

    int updateOrderResult(@Param("userId") String userId,
                          @Param("orderId") Long orderId,
                          @Param("awardId") Integer awardId,
                          @Param("awardType") Integer awardType);
}
