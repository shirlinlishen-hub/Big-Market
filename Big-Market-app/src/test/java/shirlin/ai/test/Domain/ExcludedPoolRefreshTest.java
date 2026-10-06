package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyEntity;
import shirlin.ai.domain.strategy.model.entity.StrategyRuleEntity;
import shirlin.ai.domain.strategy.service.Armory.StrategyArmory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcludedPoolRefreshTest {

    @Test
    void excludedPoolUsesCurrentMysqlAwardsOnEveryRequest() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        when(repository.queryStrategyById(1L)).thenReturn(StrategyEntity.builder()
                .strategyId(1L).totalProbability(BigDecimal.ONE)
                .probabilityPrecision(100).status(1).build());
        when(repository.queryStrategyRuleByModel(1L, "rule_fallback")).thenReturn(
                StrategyRuleEntity.builder().ruleValue("{\"award_id\":106}").build());
        when(repository.queryStrategyAwardListById(1L)).thenReturn(
                List.of(award(101, "0.50", 10), award(102, "0.50", 10),
                        award(106, "0.00", -1)),
                List.of(award(101, "0.50", 10), award(103, "0.50", 10),
                        award(106, "0.00", -1)));
        StrategyArmory armory = new StrategyArmory();
        ReflectionTestUtils.setField(armory, "strategyRepository", repository);

        assertEquals(102, armory.getRandomAwardId(1L, Set.of(101, 106)));
        assertEquals(103, armory.getRandomAwardId(1L, Set.of(101, 106)));
    }

    private StrategyAwardEntity award(int awardId, String rate, int count) {
        return StrategyAwardEntity.builder()
                .strategyId(1L)
                .awardId(awardId)
                .awardType(1)
                .awardRate(new BigDecimal(rate))
                .awardCount(count)
                .awardSurplus(count)
                .build();
    }
}
