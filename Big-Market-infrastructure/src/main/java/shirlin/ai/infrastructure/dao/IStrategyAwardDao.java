package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.StrategyAward;

import java.util.List;

@Mapper
public interface IStrategyAwardDao {
    int insert(StrategyAward strategyAwardPO);

    int updateById(StrategyAward strategyAwardPO);

    int deleteById(Long id);

    StrategyAward selectById(Long id);

    List<StrategyAward> selectByStrategyId(Long strategyId);

    StrategyAward selectByStrategyIdAndAwardId(@Param("strategyId") Long strategyId, @Param("awardId") Integer awardId);

    int batchInsert(List<StrategyAward> list);

}
