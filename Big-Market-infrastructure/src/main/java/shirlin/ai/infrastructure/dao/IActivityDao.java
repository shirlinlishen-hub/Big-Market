package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import shirlin.ai.infrastructure.dao.po.Activity;

import java.util.List;

@Mapper
public interface IActivityDao {

    int insert(Activity activityPO);

    int updateById(Activity activityPO);

    int deleteById(Long id);

    Activity selectById(Long id);

    Activity selectByActivityId(Long activityId);

    Activity selectByStrategyId(Long strategyId);

    List<Activity> selectAll();

    List<Activity> selectByStatus(Integer status);
}
