package shirlin.ai.domain.strategy.service.Armory;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.model.entity.RuleBlacklistConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleWeightConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.IStrategyArmory;
import shirlin.ai.domain.strategy.service.Rule.FallbackAwardPolicy;
import shirlin.ai.domain.strategy.service.Rule.PostDrawRulePolicy;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

@Service
public class StrategyArmory  implements IStrategyArmory {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private IStrategyRepository strategyRepository;


    /**
     * 策略装配 -- 生成概率区间并缓存到Redis中
     * 调用 -- 当前Strategy上线后/应用启动时预热
     * @param StrategyId -- 抽奖活动
     */
    @Override
    public void assembleLotteryStrategy(Long StrategyId) {

        //1. 查询当前Strategy下的所有奖品
        List<StrategyAwardEntity> awards = strategyRepository.queryStrategyAwardListById(StrategyId);
        if (awards == null || awards.isEmpty()) {
            throw new IllegalArgumentException("Strategy has no awards");
        }

        //2. 获取概率精度
        StrategyEntity strategy = strategyRepository.queryStrategyById(StrategyId);
        if (strategy == null || strategy.getProbabilityPrecision() == null) {
            throw new IllegalArgumentException("Strategy probability configuration is missing");
        }
        if (strategy.getTotalProbability() == null
                || strategy.getTotalProbability().compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Strategy total probability must equal 1");
        }
        int precision = strategy.getProbabilityPrecision();

        //3. 生成概率区间表
        Map<Integer, BigDecimal> defaultRates = new HashMap<>();
        for (StrategyAwardEntity award : awards) {
            if (award == null || award.getAwardId() == null || award.getAwardId() <= 0
                    || defaultRates.containsKey(award.getAwardId())) {
                throw new IllegalArgumentException("Strategy awards contain a missing or duplicate ID");
            }
            defaultRates.put(award.getAwardId(), award.getAwardRate());
            if (award.getAwardCount() == null || award.getAwardSurplus() == null
                    || award.getAwardCount() < -1
                    || (award.getAwardCount() == -1 && award.getAwardSurplus() != -1)
                    || (award.getAwardCount() >= 0 && (award.getAwardSurplus() < 0
                    || award.getAwardSurplus() > award.getAwardCount()))) {
                throw new IllegalArgumentException("Strategy award stock configuration is invalid");
            }
        }
        List<AwardRateRange> rangeTable = ProbabilityTableBuilder.build(defaultRates, precision, true);
        StrategyRuleEntity fallbackRule = strategyRepository.queryStrategyRuleByModel(StrategyId, "rule_fallback");
        int fallbackId = FallbackAwardPolicy.requireValid(
                fallbackRule == null ? null : fallbackRule.getRuleValue(), awards);
        StrategyRuleEntity lockRule = strategyRepository.queryStrategyRuleByModel(StrategyId, "rule_lock");
        StrategyRuleEntity luckRule = strategyRepository.queryStrategyRuleByModel(StrategyId, "rule_luck");
        RuleLockConfigEntity lockConfig = lockRule == null ? null
                : JSON.parseObject(lockRule.getRuleValue(), RuleLockConfigEntity.class);
        RuleLuckConfigEntity luckConfig = luckRule == null ? null
                : JSON.parseObject(luckRule.getRuleValue(), RuleLuckConfigEntity.class);
        if ((lockRule != null && lockConfig == null) || (luckRule != null && luckConfig == null)) {
            throw new IllegalArgumentException("Post-draw rule configuration is empty");
        }
        PostDrawRulePolicy.validate(lockConfig, luckConfig, fallbackId);
        if (lockConfig != null && !defaultRates.keySet().containsAll(lockConfig.getLockedAwardIds())) {
            throw new IllegalArgumentException("Lock rule references an unknown award");
        }
        StrategyRuleEntity blacklistRule = strategyRepository.queryStrategyRuleByModel(StrategyId, "rule_blacklist");
        if (blacklistRule != null) {
            RuleBlacklistConfigEntity blacklist = JSON.parseObject(
                    blacklistRule.getRuleValue(), RuleBlacklistConfigEntity.class);
            if (blacklist == null || !Integer.valueOf(fallbackId).equals(blacklist.getAwardId())) {
                throw new IllegalArgumentException("Blacklist award must be the configured fallback");
            }
        }

        StrategyRuleEntity weightRule = strategyRepository.queryStrategyRuleByModel(
                StrategyId, RuleTypeVO.RULEWEIGHT.getRuleModel());
        Map<String, List<AwardRateRange>> weightTables = new HashMap<>();
        if (weightRule != null) {
            RuleWeightConfigEntity weightConfig = JSON.parseObject(weightRule.getRuleValue(), RuleWeightConfigEntity.class);
            if (weightConfig == null || weightConfig.getGroups() == null || weightConfig.getGroups().isEmpty()
                    || !"draw_count".equals(weightConfig.getThresholdKey())) {
                throw new IllegalArgumentException("Unsupported or empty weight rule configuration");
            }
            Set<Integer> thresholds = new HashSet<>();
            for (RuleWeightConfigEntity.WeightGroup group : weightConfig.getGroups()) {
                if (group == null || group.getGroupId() == null || group.getGroupId().isBlank()
                        || group.getThresholdValue() == null || group.getThresholdValue() < 0
                        || !thresholds.add(group.getThresholdValue())
                        || group.getAwardRates() == null || group.getAwardRates().isEmpty()
                        || !defaultRates.keySet().containsAll(group.getAwardRates().keySet())) {
                    throw new IllegalArgumentException("Invalid weight group configuration");
                }
                List<AwardRateRange> table = ProbabilityTableBuilder.build(group.getAwardRates(), precision, true);
                if (weightTables.putIfAbsent(group.getGroupId(), table) != null) {
                    throw new IllegalArgumentException("Duplicate weight group ID");
                }
            }
        }

        // Publish only after validating the complete strategy configuration.
        strategyRepository.storeStrategyAwardRangeTable(StrategyId, rangeTable);
        strategyRepository.storeStrategyPrecision(StrategyId, precision);
        weightTables.forEach((groupId, table) ->
                strategyRepository.storeWeightRangeTable(StrategyId, groupId, table));

        // All pools have been validated before publication.
    }

    /**
     * 抽奖 -- 二分查找命中奖品
     * @param StrategyId
     * @return
     */
    @Override
    public Integer getRandomAwardId(Long StrategyId) {
        //获取精度
        int precision = strategyRepository.getStrategyPrecision(StrategyId);
        // Ticket range: [0, precision).
        int randomValue = RANDOM.nextInt(precision);

        //从Redis中取出RangeTbale
        List<AwardRateRange> rangeTable = strategyRepository.getStrategyRangeTable(StrategyId);

        //二分查找
        return ProbabilityTableBuilder.pick(rangeTable, randomValue);
    }

    /**
     * 权重分组抽奖 -- 使用预装配的权重专属区间表
     */
    @Override
    public Integer getRandomAwardId(Long strategyId, String weightGroupId) {
        List<AwardRateRange> weightTable = strategyRepository.getWeightRangeTable(strategyId, weightGroupId);
        if (weightTable == null || weightTable.isEmpty()) {
            throw new IllegalStateException("Configured weight pool is missing: " + weightGroupId);
        }
        int upperBound = weightTable.get(weightTable.size() - 1).getRangeEnd();
        int randomVal = RANDOM.nextInt(upperBound);
        return ProbabilityTableBuilder.pick(weightTable, randomVal);
    }

    /**
     * 带排除的抽奖 -- 当某些奖品被规则过滤掉，在剩余奖品中计算
     * @param strategyId
     * @param excludeAwardIds
     * @return
     */
    @Override
    public Integer getRandomAwardId(Long strategyId, Set<Integer> excludeAwardIds) {
        // 如果没有排除项, 走常规逻辑
        if (excludeAwardIds == null || excludeAwardIds.isEmpty()) {
            return getRandomAwardId(strategyId);
        }
        List<AwardRateRange> subTable = strategyRepository.buildSubRangeTable(strategyId, excludeAwardIds);
        if (subTable == null || subTable.isEmpty()) {
            throw new IllegalArgumentException("No awards remain after exclusions");
        }
        int subPrecision = subTable.get(subTable.size() - 1).getRangeEnd();
        int randomVal = RANDOM.nextInt(subPrecision);
        return ProbabilityTableBuilder.pick(subTable, randomVal);
    }

}
