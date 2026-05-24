package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory;


import shirlin.ai.types.design.link.model2.LinkArmory;
import shirlin.ai.types.design.link.model2.chain.BusinessLinkedList;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter.RuleBlacklistFilter;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter.RuleWeightFilter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
/**
 * 抽奖前规则过滤
 * 黑名单规则 -> 权重规则
 */
public class StrategyPreRuleFilterFactory {

    @Resource
    private RuleWeightFilter ruleWeightFilter;

    @Resource
    private RuleBlacklistFilter ruleBlacklistFilter;


    @Bean("strategyPreRuleFilter")
    public BusinessLinkedList<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> strategyPreRuleHandler() {

        // 组装链
        LinkArmory<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> linkArmory =
                new LinkArmory<>("交易规则过滤链", ruleBlacklistFilter, ruleWeightFilter);

        return linkArmory.getLogicLink();
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        /** 命中的权重分组ID，用于装配期预构建的区间表查找 **/
        private String weightGroupId;
        /** 概率配置表 **/
        private Map<Integer, BigDecimal> awardRates;
        /** 排除的奖品ID **/
        private Set<Integer> excludeAwardIds = new HashSet<>();
    }
}
