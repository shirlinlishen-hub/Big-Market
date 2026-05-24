package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import shirlin.ai.infrastructure.dao.po.Strategy;

import java.util.List;

@Mapper
public interface IStrategyDao {
    int insert(Strategy strategyPO);

    int updateById(Strategy strategyPO);

    int deleteById(Long id);

    Strategy selectById(Long id);

    Strategy selectByStrategyId(Long strategyId);

    List<Strategy> selectAll();

    List<Strategy> selectByStatus(Integer status);
}