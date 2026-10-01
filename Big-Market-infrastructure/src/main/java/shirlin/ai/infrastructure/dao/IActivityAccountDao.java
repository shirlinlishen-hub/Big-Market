package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;

@Mapper
public interface IActivityAccountDao {

    int insert(ActivityAccount activityAccountPO);


    ActivityAccount selectByUserIdAndActivityId(@Param("userId") String userId, @Param("activityId") Long activityId);
    ActivityAccount selectForUpdate(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减总剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductTotalSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减月剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductMonthSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减日剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductDaySurplus(@Param("userId") String userId, @Param("activityId") Long activityId);

    int grantDrawRights(@Param("userId") String userId, @Param("activityId") Long activityId,
                        @Param("totalCount") Integer totalCount,
                        @Param("monthCount") Integer monthCount,
                        @Param("dayCount") Integer dayCount);

    int restoreTotalSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);
    int grantTotalOnly(@Param("userId") String userId, @Param("activityId") Long activityId,
                       @Param("count") Integer count);
    int revokeFiniteRights(@Param("userId") String userId, @Param("activityId") Long activityId,
                           @Param("grantedCount") Integer grantedCount,
                           @Param("removeSurplus") Integer removeSurplus);
    int quarantineSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);
    int setPeriodLimits(@Param("userId") String userId, @Param("activityId") Long activityId,
                        @Param("monthCount") Integer monthCount, @Param("dayCount") Integer dayCount);
}
