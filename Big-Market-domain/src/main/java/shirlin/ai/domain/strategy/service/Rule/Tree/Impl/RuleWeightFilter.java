package shirlin.ai.domain.strategy.service.Rule.Impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.AbstractRuleFilterService;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权重规则（责任链第二节点）
 * ruleValue 格式: {"4000":[102,103,104],"6000":[101,102,103,104]}
 * key = 累计抽奖次数阈值；value = 该权重等级可参与的奖品ID列表
 * 找到用户命中的最高阈值，将不在该奖池内的奖品加入 excludeAwardIds
 */
@Slf4j
@Service("ruleWeightFilter")
public class RuleWeightFilter extends AbstractRuleFilterService {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULEWEIGHT.getRuleModel());

        // 策略未配置权重规则，放行（走默认全奖池）
        if (rule == null || rule.getRuleValue() == null) {
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.ALLOW)
                    .excludeAwardIds(Collections.emptySet())
                    .build();
        }

        Map<String, List<Integer>> weightMap = JSON.parseObject(
                rule.getRuleValue(), new TypeReference<Map<String, List<Integer>>>() {});

        int userWeightValue = strategyRepository.queryUserWeightValue(
                factory.getUserId(), factory.getStrategyId());

        // 找到用户能命中的最高阈值对应的奖池
        List<Integer> allowedAwardIds = null;
        int highestThreshold = 0;
        for (Map.Entry<String, List<Integer>> entry : weightMap.entrySet()) {
            int threshold = Integer.parseInt(entry.getKey());
            if (userWeightValue >= threshold && threshold > highestThreshold) {
                highestThreshold = threshold;
                allowedAwardIds = entry.getValue();
            }
        }

        // 用户未达到任何阈值，走默认全奖池
        if (allowedAwardIds == null) {
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.ALLOW)
                    .excludeAwardIds(Collections.emptySet())
                    .build();
        }

        // 计算不在权重奖池内的奖品，加入排除集合
        List<StrategyAwardEntity> allAwards = strategyRepository.queryStrategyAwardListById(factory.getStrategyId());
        Set<Integer> excludeAwardIds = new HashSet<>();
        for (StrategyAwardEntity award : allAwards) {
            if (!allowedAwardIds.contains(award.getAwardId())) {
                excludeAwardIds.add(award.getAwardId());
            }
        }

        log.info("权重命中 userId:{} threshold:{} excludeCount:{}", factory.getUserId(), highestThreshold, excludeAwardIds.size());
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .excludeAwardIds(excludeAwardIds)
                .build();
    }

}
