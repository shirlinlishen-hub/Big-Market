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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyArmoryMysqlSourceTest {

    @Test
    void nextDrawUsesChangedMysqlProbabilityWithoutAssembly() {
        IStrategyRepository repository = repositoryWithCommonConfiguration();
        when(repository.queryStrategyAwardListById(1L)).thenReturn(
                List.of(award(101, "1.00", 10), award(106, "0.00", -1)),
                List.of(award(102, "1.00", 10), award(106, "0.00", -1)));
        StrategyArmory armory = armory(repository);

        assertEquals(101, armory.getRandomAwardId(1L));
        assertEquals(102, armory.getRandomAwardId(1L));
    }

    @Test
    void weightPoolUsesCurrentMysqlRule() {
        IStrategyRepository repository = repositoryWithCommonConfiguration();
        when(repository.queryStrategyAwardListById(1L)).thenReturn(
                List.of(award(101, "0.50", 10), award(106, "0.50", -1)));
        when(repository.queryStrategyRuleByModel(1L, "rule_weight")).thenReturn(rule(
                "{\"threshold_key\":\"draw_count\",\"groups\":[{" +
                        "\"threshold_value\":10,\"group_id\":\"vip\"," +
                        "\"award_rates\":{\"101\":1.00,\"106\":0.00}}]}"));

        assertEquals(101, armory(repository).getRandomAwardId(1L, "vip"));
    }

    @Test
    void missingWeightGroupCannotFallBackToDefaultPool() {
        StrategyArmory armory = armory(validRepository());

        assertThrows(IllegalStateException.class,
                () -> armory.getRandomAwardId(1L, "removed-group"));
    }

    @Test
    void excludingEveryAwardIsRejected() {
        StrategyArmory armory = armory(validRepository());

        assertThrows(IllegalArgumentException.class,
                () -> armory.getRandomAwardId(1L, Set.of(101, 106)));
    }

    private IStrategyRepository validRepository() {
        IStrategyRepository repository = repositoryWithCommonConfiguration();
        when(repository.queryStrategyAwardListById(1L)).thenReturn(
                List.of(award(101, "0.50", 10), award(106, "0.50", -1)));
        return repository;
    }

    private IStrategyRepository repositoryWithCommonConfiguration() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        when(repository.queryStrategyById(1L)).thenReturn(StrategyEntity.builder()
                .strategyId(1L)
                .totalProbability(BigDecimal.ONE)
                .probabilityPrecision(100)
                .status(1)
                .build());
        when(repository.queryStrategyRuleByModel(1L, "rule_fallback"))
                .thenReturn(rule("{\"award_id\":106}"));
        return repository;
    }

    private StrategyArmory armory(IStrategyRepository repository) {
        StrategyArmory armory = new StrategyArmory();
        ReflectionTestUtils.setField(armory, "strategyRepository", repository);
        return armory;
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

    private StrategyRuleEntity rule(String value) {
        return StrategyRuleEntity.builder()
                .strategyId(1L)
                .ruleValue(value)
                .build();
    }
}
