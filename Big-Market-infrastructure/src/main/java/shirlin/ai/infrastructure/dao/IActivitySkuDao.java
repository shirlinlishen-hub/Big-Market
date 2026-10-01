package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.ActivitySku;

import java.util.List;

@Mapper
public interface IActivitySkuDao {

    int insert(ActivitySku activitySkuPO);

    int updateById(ActivitySku activitySkuPO);

    int deleteById(Long id);

    ActivitySku selectById(Long id);

    ActivitySku selectBySkuId(Long skuId);

    List<ActivitySku> selectByActivityId(Long activityId);

    List<ActivitySku> selectByActivityIdAndStatus(@Param("activityId") Long activityId, @Param("status") Integer status);

}
