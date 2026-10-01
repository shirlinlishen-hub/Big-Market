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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StrategyArmoryValidationTest {
    @Test
    void validConfiguredRulesPublishDefaultAndWeightPools() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        StrategyArmory armory = new StrategyArmory();
        ReflectionTestUtils.setField(armory, "strategyRepository", repository);
        when(repository.queryStrategyById(1L)).thenReturn(StrategyEntity.builder()
                .strategyId(1L).totalProbability(BigDecimal.ONE).probabilityPrecision(100).build());
        when(repository.queryStrategyAwardListById(1L)).thenReturn(List.of(
                StrategyAwardEntity.builder().awardId(101).awardRate(new BigDecimal("0.40"))
                        .awardCount(1).awardSurplus(1).build(),
                StrategyAwardEntity.builder().awardId(106).awardRate(new BigDecimal("0.60"))
                        .awardCount(-1).awardSurplus(-1).build()));
        when(repository.queryStrategyRuleByModel(1L, "rule_fallback")).thenReturn(
                StrategyRuleEntity.builder().ruleValue("{\"award_id\":106}").build());
        when(repository.queryStrategyRuleByModel(1L, "rule_lock")).thenReturn(
                StrategyRuleEntity.builder().ruleValue(
                        "{\"unlock_count\":10,\"locked_award_ids\":[101]}").build());
        when(repository.queryStrategyRuleByModel(1L, "rule_luck")).thenReturn(
                StrategyRuleEntity.builder().ruleValue(
                        "{\"luck_threshold\":50,\"luck_award_id\":106,\"luck_increment\":1}").build());
        when(repository.queryStrategyRuleByModel(1L, "rule_weight")).thenReturn(
                StrategyRuleEntity.builder().ruleValue(
                        "{\"threshold_key\":\"draw_count\",\"groups\":[{" +
                                "\"threshold_value\":10,\"group_id\":\"group_10\"," +
                                "\"award_rates\":{\"101\":0.20,\"106\":0.80}}]}").build());

        armory.assembleLotteryStrategy(1L);

        verify(repository).storeStrategyAwardRangeTable(eq(1L), argThat(ranges ->
                ranges.size() == 2 && ranges.get(0).getRangeStart() == 0
                        && ranges.get(0).getRangeEnd() == 40 && ranges.get(1).getRangeEnd() == 100));
        verify(repository).storeWeightRangeTable(eq(1L), eq("group_10"), argThat(ranges ->
                ranges.size() == 2 && ranges.get(0).getRangeEnd() == 20
                        && ranges.get(1).getRangeEnd() == 100));
        verify(repository).storeStrategyPrecision(1L, 100);
    }

    @Test
    void invalidPoolIsRejectedBeforeAnyCacheIsPublished() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        StrategyArmory armory = new StrategyArmory();
        ReflectionTestUtils.setField(armory, "strategyRepository", repository);
        when(repository.queryStrategyById(1L)).thenReturn(StrategyEntity.builder()
                .strategyId(1L).totalProbability(BigDecimal.ONE).probabilityPrecision(100).build());
        when(repository.queryStrategyAwardListById(1L)).thenReturn(List.of(
                StrategyAwardEntity.builder().awardId(101).awardRate(new BigDecimal("0.4"))
                        .awardCount(-1).awardSurplus(-1).build()));

        assertThrows(IllegalArgumentException.class, () -> armory.assembleLotteryStrategy(1L));
        verify(repository, never()).storeStrategyAwardRangeTable(anyLong(), anyList());
    }

    @Test
    void invalidConfiguredLuckRuleIsRejectedBeforePublishingPool() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        StrategyArmory armory = new StrategyArmory();
        ReflectionTestUtils.setField(armory, "strategyRepository", repository);
        when(repository.queryStrategyById(1L)).thenReturn(StrategyEntity.builder()
                .strategyId(1L).totalProbability(BigDecimal.ONE).probabilityPrecision(100).build());
        when(repository.queryStrategyAwardListById(1L)).thenReturn(List.of(
                StrategyAwardEntity.builder().awardId(106).awardRate(BigDecimal.ONE)
                        .awardCount(-1).awardSurplus(-1).build()));
        when(repository.queryStrategyRuleByModel(1L, "rule_fallback")).thenReturn(
                StrategyRuleEntity.builder().ruleValue("{\"award_id\":106}").build());
        when(repository.queryStrategyRuleByModel(1L, "rule_luck")).thenReturn(
                StrategyRuleEntity.builder().ruleValue(
                        "{\"luck_threshold\":0,\"luck_award_id\":106,\"luck_increment\":1}").build());

        assertThrows(IllegalArgumentException.class, () -> armory.assembleLotteryStrategy(1L));
        verify(repository, never()).storeStrategyAwardRangeTable(anyLong(), anyList());
    }
}
