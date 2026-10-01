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

import java.math.BigDecimal;
import java.util.Map;


@Slf4j
@Service
public class RuleWeightFilter implements ILogicHandler<RaffleFactorEntity, StrategyPreRuleFilterFactory.DynamicContext, RuleFilterResultEntity> {

    @Resource
    private IStrategyRepository strategyRepository;


    @Override
    public RuleFilterResultEntity apply(RaffleFactorEntity factory, StrategyPreRuleFilterFactory.DynamicContext dynamicContext) throws Exception {

        //1. 查询strategy是否配置了权重规则
        StrategyRuleEntity rule = strategyRepository.queryStrategyRuleByModel(
                factory.getStrategyId(), RuleTypeVO.RULEWEIGHT.getRuleModel());

        // 策略未配置权重规则，放行（走默认全奖池）
        if (rule == null || rule.getRuleValue() == null) {
            return null;
        }

        //获取权重配置规则
        RuleWeightConfigEntity weightConfig = JSON.parseObject(rule.getRuleValue(), RuleWeightConfigEntity.class);
        if (weightConfig == null || !"draw_count".equals(weightConfig.getThresholdKey())) {
            throw new IllegalArgumentException("Unsupported weight threshold metric");
        }

        //获取用户的thresholdValue值
        int userThresholdValue = strategyRepository.queryUserThresholdValue(
                factory.getUserId(), factory.getStrategyId());

        // 找到用户命中的权重分组
        RuleWeightConfigEntity.WeightGroup matchedGroup = weightConfig.getMatchedGroup(userThresholdValue);
        // 用户未达到任何阈值，走默认全奖池
        if (matchedGroup == null) {
            return null;
        }

        Map<Integer, BigDecimal> awardRates = matchedGroup.getAwardRates();
        log.info("权重命中 userId:{} groupId:{} threshold:{}", factory.getUserId(),
                matchedGroup.getGroupId(), matchedGroup.getThresholdValue());

        dynamicContext.setWeightGroupId(matchedGroup.getGroupId());
        dynamicContext.setAwardRates(awardRates);
        return null;
    }
}
