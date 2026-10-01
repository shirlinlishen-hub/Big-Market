package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IUsagePeriodDao {
    int insertIfAbsent(@Param("userId") String userId, @Param("activityId") Long activityId,
                       @Param("periodType") String periodType, @Param("periodKey") String periodKey);
    int incrementWithinLimit(@Param("userId") String userId, @Param("activityId") Long activityId,
                             @Param("periodType") String periodType, @Param("periodKey") String periodKey,
                             @Param("limit") Integer limit);
    int decrementIfPositive(@Param("userId") String userId, @Param("activityId") Long activityId,
                            @Param("periodType") String periodType, @Param("periodKey") String periodKey);
}
