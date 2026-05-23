package shirlin.ai.domain.strategy.service.Rule.Tree.Impl;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.Tree.AbstractRuleFilterService;

/**
 * N次解锁规则（规则树 Lock 节点）
 * 检查用户累计抽奖次数是否满足解锁条件；结果由规则树内联使用，不作为责任链节点调用
 */
@Slf4j
@Service("ruleLockFilter")
public class RuleLockFilter extends AbstractRuleFilterService {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULELOCK.getRuleModel());

        if (rule == null) {
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.ALLOW)
                    .build();
        }

        RuleLockConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleLockConfigEntity.class);
        int usedCount = strategyRepository.queryUserDrawCount(factory.getUserId(), factory.getStrategyId());

        if (usedCount < config.getUnlockCount()) {
            log.info("Lock 未解锁 userId:{} usedCount:{} unlockCount:{}",
                    factory.getUserId(), usedCount, config.getUnlockCount());
        }

        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }

}
