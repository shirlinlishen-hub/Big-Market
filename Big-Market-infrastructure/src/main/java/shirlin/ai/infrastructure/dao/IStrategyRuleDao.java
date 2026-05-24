package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.StrategyRule;

import java.util.List;

@Mapper
public interface IStrategyRuleDao {
    int insert(StrategyRule strategyRulePO);

    int updateById(StrategyRule strategyRulePO);

    int deleteById(Long id);

    StrategyRule selectById(Long id);

    List<StrategyRule> selectByStrategyId(Long strategyId);

    StrategyRule selectByStrategyIdAndRuleModel(@Param("strategyId") Long strategyId, @Param("ruleModel") String ruleModel);

    int batchInsert(List<StrategyRule> list);
}