package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.ActivityAccount;

@Mapper
public interface IActivityAccountDao {

    int insert(ActivityAccount activityAccountPO);

    int updateById(ActivityAccount activityAccountPO);

    int deleteById(Long id);

    ActivityAccount selectById(Long id);

    ActivityAccount selectByUserIdAndActivityId(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减总剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductTotalSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减月剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductMonthSurplus(@Param("userId") String userId, @Param("activityId") Long activityId);

    /** 乐观扣减日剩余次数；返回影响行数 1=成功 0=额度不足 */
    int deductDaySurplus(@Param("userId") String userId, @Param("activityId") Long activityId);
}
