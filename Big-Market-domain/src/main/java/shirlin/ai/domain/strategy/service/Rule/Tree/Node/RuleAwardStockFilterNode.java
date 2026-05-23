package shirlin.ai.domain.strategy.service.Rule.Tree.Node;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.RaffleFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RuleFilterResultEntity;
import shirlin.ai.domain.strategy.service.Rule.Tree.AbstractRuleFilterService;

@Slf4j
@Service("ruleAwardStockFilter")
public class RuleAwardStockFilter extends AbstractRuleFilterService {

    @Resource
    private IStrategyRepository strategyRepository;


    @Override
    protected RuleFilterResultEntity doFilter(RaffleFactorEntity factory) {
        // Stock 扣减逻辑已内联至 DefaultRaffleStrategy.doAfterRaffleRuleFilter；此节点预留，暂不启用
        return RuleFilterResultEntity.builder()
                .type(RuleFilterResultEntity.Type.ALLOW)
                .build();
    }
}
