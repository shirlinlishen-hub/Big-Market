package shirlin.ai.domain.strategy.service.Rule.Tree.Impl;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.Tree.AbstractRuleFilterService;

/**
 * 运气值兜底规则（保留备用；当前主流程不调用此节点）
 * 当用户运气值累积达到阈值时，强制给定兜底奖品并重置运气值
 */
@Slf4j
@Service("ruleLuckFilter")
public class RuleLuckFilter extends AbstractRuleFilterService {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {

        //1. 查找是否存在兜底规则 rule_luck
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULELUCK.getRuleModel());

        if (rule == null) {
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.ALLOW)
                    .build();
        }

        RuleLuckConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleLuckConfigEntity.class);
        int currentLuck = strategyRepository.queryUserLuckValue(factory.getUserId(), factory.getStrategyId());

        if (currentLuck >= config.getLuckCount()) {
            strategyRepository.resetUserLuckValue(factory.getUserId(), factory.getStrategyId());
            log.info("运气值达到阈值 userId:{} luckCount:{}", factory.getUserId(), config.getLuckCount());
            //返回兜底产品
            return RuleFilterResultEntity.builder()
                    .type(RuleFilterResultEntity.Type.TAKE_OVER)
                    .awardId(config.getAwardId())
                    .build();
        }

        //继续后续流程
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }

}
