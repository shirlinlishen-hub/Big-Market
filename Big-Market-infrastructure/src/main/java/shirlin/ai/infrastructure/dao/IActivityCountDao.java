package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.ActivityCount;

@Mapper
public interface IActivityCountDao {
    ActivityCount selectByActivityCountId(@Param("activityCountId") Long activityCountId);
}
