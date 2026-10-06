package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.service.Rule.FallbackAwardPolicy;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.Strategy;
import shirlin.ai.infrastructure.dao.po.StrategyAward;
import shirlin.ai.infrastructure.dao.po.StrategyRule;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IStrategyDao strategyDao;

    @Resource
    private IStrategyAwardDao strategyAwardDao;

    @Resource
    private IStrategyRuleDao strategyRuleDao;

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    // ---- Strategy / Award 基础查询 ----

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardListById(Long strategyId) {
        List<StrategyAward> pos = strategyAwardDao.selectByStrategyId(strategyId);
        return pos.stream().map(po -> StrategyAwardEntity.builder()
                .strategyId(po.getStrategyId())
                .awardId(po.getAwardId())
                .awardType(po.getAwardType())
                .awardCount(po.getAwardCount())
                .awardSurplus(po.getAwardSurplus())
                .awardRate(po.getAwardRate())
                .sort(po.getSort())
                .ruleModels(po.getRuleModels())
                .build()).collect(Collectors.toList());
    }

    @Override
    public StrategyAwardEntity queryStrategyAward(Long strategyId, Integer awardId) {
        StrategyAward po = strategyAwardDao.selectByStrategyIdAndAwardId(strategyId, awardId);
        if (po == null) return null;
        return StrategyAwardEntity.builder()
                .strategyId(po.getStrategyId())
                .awardId(po.getAwardId())
                .awardType(po.getAwardType())
                .awardCount(po.getAwardCount())
                .awardSurplus(po.getAwardSurplus())
                .awardRate(po.getAwardRate())
                .sort(po.getSort())
                .ruleModels(po.getRuleModels())
                .build();
    }

    @Override
    public StrategyEntity queryStrategyById(Long strategyId) {
        Strategy po = strategyDao.selectByStrategyId(strategyId);
        if (po == null) return null;
        return StrategyEntity.builder()
                .strategyId(po.getStrategyId())
                .totalProbability(po.getTotalProbability())
                .probabilityPrecision(po.getProbabilityPrecision())
                .freeDrawCount(po.getFreeDrawCount())
                .pointsPerDraw(po.getPointsPerDraw())
                .status(po.getStatus())
                .beginTime(po.getBeginTime())
                .endTime(po.getEndTime())
                .build();
    }

    // ---- 规则配置查询 ----

    @Override
    public StrategyRuleEntity queryStrategyRuleByModel(Long strategyId, String ruleModel) {
        StrategyRule po = strategyRuleDao.selectByStrategyIdAndRuleModel(strategyId, ruleModel);
        if (po == null) return null;
        return StrategyRuleEntity.builder()
                .strategyId(po.getStrategyId())
                .ruleType(po.getRuleType())
                .ruleModel(po.getRuleModel())
                .ruleValue(po.getRuleValue())
                .ruleDesc(po.getRuleDesc())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    @Override
    public Integer queryFallbackAwardId(Long strategyId) {
        StrategyRule rule = strategyRuleDao.selectByStrategyIdAndRuleModel(strategyId, "rule_fallback");
        return FallbackAwardPolicy.requireValid(
                rule == null ? null : rule.getRuleValue(), queryStrategyAwardListById(strategyId));
    }

    // ---- 用户状态查询 ----

    @Override
    public int queryUserDrawCount(String userId, Long strategyId) {
        return userAwardRecordDao.countByUserIdAndStrategyId(userId, strategyId);
    }

    @Override
    public int queryUserThresholdValue(String userId, Long strategyId) {

        // 当前以累计抽奖次数作为权重值，可按需替换为积分/会员等级等
        return userAwardRecordDao.countByUserIdAndStrategyId(userId, strategyId);
    }

}
