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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StrategyArmory implements IStrategyArmory {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public void assembleLotteryStrategy(Long strategyId) {
        loadDefinition(strategyId);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        return pick(loadDefinition(strategyId).defaultTable());
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, String weightGroupId) {
        StrategyDefinition definition = loadDefinition(strategyId);
        List<AwardRateRange> table = definition.weightTables().get(weightGroupId);
        if (table == null || table.isEmpty()) {
            throw new IllegalStateException(
                    "Configured weight pool is missing: " + weightGroupId);
        }
        return pick(table);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, Set<Integer> excludeAwardIds) {
        StrategyDefinition definition = loadDefinition(strategyId);
        if (excludeAwardIds == null || excludeAwardIds.isEmpty()) {
            return pick(definition.defaultTable());
        }

        Map<Integer, BigDecimal> remainingRates = new HashMap<>();
        for (StrategyAwardEntity award : definition.awards()) {
            if (!excludeAwardIds.contains(award.getAwardId())) {
                remainingRates.put(award.getAwardId(), award.getAwardRate());
            }
        }
        if (remainingRates.isEmpty()) {
            throw new IllegalArgumentException("No awards remain after exclusions");
        }
        return pick(ProbabilityTableBuilder.build(
                remainingRates, definition.precision(), false));
    }

    private Integer pick(List<AwardRateRange> table) {
        if (table == null || table.isEmpty()) {
            throw new IllegalArgumentException("Probability pool is empty");
        }
        int upperBound = table.get(table.size() - 1).getRangeEnd();
        return ProbabilityTableBuilder.pick(table, RANDOM.nextInt(upperBound));
    }

    private StrategyDefinition loadDefinition(Long strategyId) {
        if (strategyId == null) {
            throw new IllegalArgumentException("Strategy ID is required");
        }

        List<StrategyAwardEntity> awards = strategyRepository
                .queryStrategyAwardListById(strategyId);
        if (awards == null || awards.isEmpty()) {
            throw new IllegalArgumentException("Strategy has no awards");
        }

        StrategyEntity strategy = strategyRepository.queryStrategyById(strategyId);
        if (strategy == null || strategy.getProbabilityPrecision() == null
                || strategy.getProbabilityPrecision() <= 0) {
            throw new IllegalArgumentException(
                    "Strategy probability configuration is missing");
        }
        if (strategy.getTotalProbability() == null
                || strategy.getTotalProbability().compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Strategy total probability must equal 1");
        }
        int precision = strategy.getProbabilityPrecision();

        Map<Integer, BigDecimal> defaultRates = new HashMap<>();
        for (StrategyAwardEntity award : awards) {
            validateAward(award, defaultRates);
            defaultRates.put(award.getAwardId(), award.getAwardRate());
        }
        List<AwardRateRange> defaultTable = ProbabilityTableBuilder.build(
                defaultRates, precision, true);

        StrategyRuleEntity fallbackRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, "rule_fallback");
        int fallbackId = FallbackAwardPolicy.requireValid(
                fallbackRule == null ? null : fallbackRule.getRuleValue(), awards);
        validatePostDrawRules(strategyId, fallbackId, defaultRates.keySet());
        validateBlacklistRule(strategyId, fallbackId);
        Map<String, List<AwardRateRange>> weightTables = loadWeightTables(
                strategyId, precision, defaultRates.keySet());

        return new StrategyDefinition(
                precision,
                List.copyOf(awards),
                List.copyOf(defaultTable),
                Map.copyOf(weightTables));
    }

    private void validateAward(StrategyAwardEntity award,
                               Map<Integer, BigDecimal> currentRates) {
        if (award == null || award.getAwardId() == null || award.getAwardId() <= 0
                || currentRates.containsKey(award.getAwardId())
                || award.getAwardRate() == null) {
            throw new IllegalArgumentException(
                    "Strategy awards contain a missing or duplicate ID");
        }
        if (award.getAwardCount() == null || award.getAwardSurplus() == null
                || award.getAwardCount() < -1
                || (award.getAwardCount() == -1 && award.getAwardSurplus() != -1)
                || (award.getAwardCount() >= 0 && (award.getAwardSurplus() < 0
                || award.getAwardSurplus() > award.getAwardCount()))) {
            throw new IllegalArgumentException(
                    "Strategy award stock configuration is invalid");
        }
    }

    private void validatePostDrawRules(Long strategyId, int fallbackId,
                                       Set<Integer> awardIds) {
        StrategyRuleEntity lockRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, "rule_lock");
        StrategyRuleEntity luckRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, "rule_luck");
        RuleLockConfigEntity lockConfig = lockRule == null ? null
                : JSON.parseObject(lockRule.getRuleValue(), RuleLockConfigEntity.class);
        RuleLuckConfigEntity luckConfig = luckRule == null ? null
                : JSON.parseObject(luckRule.getRuleValue(), RuleLuckConfigEntity.class);
        if ((lockRule != null && lockConfig == null)
                || (luckRule != null && luckConfig == null)) {
            throw new IllegalArgumentException("Post-draw rule configuration is empty");
        }
        PostDrawRulePolicy.validate(lockConfig, luckConfig, fallbackId);
        if (lockConfig != null
                && !awardIds.containsAll(lockConfig.getLockedAwardIds())) {
            throw new IllegalArgumentException("Lock rule references an unknown award");
        }
    }

    private void validateBlacklistRule(Long strategyId, int fallbackId) {
        StrategyRuleEntity blacklistRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, "rule_blacklist");
        if (blacklistRule == null) {
            return;
        }
        RuleBlacklistConfigEntity blacklist = JSON.parseObject(
                blacklistRule.getRuleValue(), RuleBlacklistConfigEntity.class);
        if (blacklist == null
                || !Integer.valueOf(fallbackId).equals(blacklist.getAwardId())) {
            throw new IllegalArgumentException(
                    "Blacklist award must be the configured fallback");
        }
    }

    private Map<String, List<AwardRateRange>> loadWeightTables(
            Long strategyId, int precision, Set<Integer> awardIds) {
        StrategyRuleEntity weightRule = strategyRepository.queryStrategyRuleByModel(
                strategyId, RuleTypeVO.RULEWEIGHT.getRuleModel());
        if (weightRule == null) {
            return Map.of();
        }

        RuleWeightConfigEntity config = JSON.parseObject(
                weightRule.getRuleValue(), RuleWeightConfigEntity.class);
        if (config == null || config.getGroups() == null || config.getGroups().isEmpty()
                || !"draw_count".equals(config.getThresholdKey())) {
            throw new IllegalArgumentException(
                    "Unsupported or empty weight rule configuration");
        }

        Set<Integer> thresholds = new HashSet<>();
        Map<String, List<AwardRateRange>> tables = new HashMap<>();
        for (RuleWeightConfigEntity.WeightGroup group : config.getGroups()) {
            if (group == null || group.getGroupId() == null || group.getGroupId().isBlank()
                    || group.getThresholdValue() == null || group.getThresholdValue() < 0
                    || !thresholds.add(group.getThresholdValue())
                    || group.getAwardRates() == null || group.getAwardRates().isEmpty()
                    || !awardIds.containsAll(group.getAwardRates().keySet())) {
                throw new IllegalArgumentException("Invalid weight group configuration");
            }
            List<AwardRateRange> table = ProbabilityTableBuilder.build(
                    group.getAwardRates(), precision, true);
            if (tables.putIfAbsent(group.getGroupId(), List.copyOf(table)) != null) {
                throw new IllegalArgumentException("Duplicate weight group ID");
            }
        }
        return tables;
    }

    private record StrategyDefinition(
            int precision,
            List<StrategyAwardEntity> awards,
            List<AwardRateRange> defaultTable,
            Map<String, List<AwardRateRange>> weightTables) {
    }
}
