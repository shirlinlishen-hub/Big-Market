package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.UserDrawAccount;

import java.util.List;

@Mapper
public interface IUserDrawAccountDao {

    int insert(UserDrawAccount userDrawAccountPO);

    int updateById(UserDrawAccount userDrawAccountPO);

    int deleteById(Long id);

    UserDrawAccount selectById(Long id);

    UserDrawAccount selectByUserIdAndStrategyId(@Param("userId") String userId, @Param("strategyId") Long strategyId);

    int updateUsedCount(@Param("userId") String userId, @Param("strategyId") Long strategyId, @Param("usedCount") Integer usedCount, @Param("remainingCount") Integer remainingCount);

    int updateTotalCount(@Param("userId") String userId, @Param("strategyId") Long strategyId, @Param("totalCount") Integer totalCount, @Param("remainingCount") Integer remainingCount);

    /** 乐观扣减：WHERE remaining_draw_count > 0；返回影响行数 */
    int deductRemainingCount(@Param("userId") String userId, @Param("strategyId") Long strategyId);
}