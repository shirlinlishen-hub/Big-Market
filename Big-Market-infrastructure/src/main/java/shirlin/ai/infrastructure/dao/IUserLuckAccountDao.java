package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.UserLuckAccount;

@Mapper
public interface IUserLuckAccountDao {
    int insert(UserLuckAccount userLuckAccountPO);

    int updateById(UserLuckAccount userLuckAccountPO);

    int deleteById(Long id);

    UserLuckAccount selectById(Long id);

    UserLuckAccount selectByUserIdAndStrategyId(@Param("userId") String userId, @Param("strategyId") Long strategyId);

    int updateLuckValue(@Param("userId") String userId, @Param("strategyId") Long strategyId, @Param("luckValue") Integer luckValue);

    int incrementLuckValue(@Param("userId") String userId, @Param("strategyId") Long strategyId, @Param("increment") Integer increment);

    int resetLuckValue(@Param("userId") String userId, @Param("strategyId") Long strategyId);
}