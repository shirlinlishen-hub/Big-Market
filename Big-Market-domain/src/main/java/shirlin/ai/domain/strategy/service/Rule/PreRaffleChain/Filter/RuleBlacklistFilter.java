package shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Filter;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.*;
import shirlin.ai.domain.strategy.model.valobj.RuleTypeVO;
import shirlin.ai.domain.strategy.service.Rule.PreRaffleChain.Factory.StrategyPreRuleFilterFactory;
import shirlin.ai.types.design.link.model2.handler.ILogicHandler;

@Slf4j
@Service
public class RuleBlacklistFilter implements ILogicHandler<RaffleFactorEntity, StrategyPreRuleFilterFactory.DynamicContext, RuleFilterResultEntity> {


    @Resource
    private IStrategyRepository strategyRepository;


    @Override
    public RuleFilterResultEntity apply(RaffleFactorEntity factory, StrategyPreRuleFilterFactory.DynamicContext dynamicContext) throws Exception {
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULEBLACKLIST.getRuleModel());

        // 策略未配置黑名单规则，直接放行
        if (rule == null || rule.getRuleValue() == null) {
            return null;
        }

        RuleBlacklistConfigEntity config = JSON.parseObject(rule.getRuleValue(), RuleBlacklistConfigEntity.class);

        if (config.getUserBlacklist() != null && !config.getUserBlacklist().isBlank()) {
            for (String blackUser : config.getUserBlacklist().split(",")) {
                if (factory.getUserId().equals(blackUser.trim())) {
                    log.info("黑名单命中 userId:{} strategyId:{}", factory.getUserId(), factory.getStrategyId());
                    //返回非null，链立即停止
                    return RuleFilterResultEntity.builder()
                            .type(RuleFilterResultEntity.Type.TAKE_OVER)
                            .awardId(strategyRepository.queryFallbackAwardId(factory.getStrategyId()))
                            .build();
                }
            }
        }

        return null;
    }
}
