package shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Factory;

import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.PostRaffleRuleTree.Node.RuleLockFilterNode;
import shirlin.ai.types.design.tree.StrategyHandler;

@Slf4j
@Service
/**
 * 抽奖中 & 后 规则过滤
 * rule_lock  -> award_stock -> rule_luck -> 兜底
 */
public class DefaultLogicFactory {

    @Resource
    private RuleLockFilterNode ruleLockFilterNode;

    public StrategyHandler<RaffleFactorEntity, DynamicContext, RuleFilterResultEntity> getStrategyHandler() {
        return ruleLockFilterNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private Integer awardId;  // 本次抽中的奖品ID，Lock/Stock 节点检查用

    }
}
