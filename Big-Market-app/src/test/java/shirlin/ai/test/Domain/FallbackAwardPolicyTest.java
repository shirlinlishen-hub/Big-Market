package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.entity.StrategyAwardEntity;
import shirlin.ai.domain.strategy.service.Rule.FallbackAwardPolicy;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallbackAwardPolicyTest {
    private final List<StrategyAwardEntity> awards = List.of(
            StrategyAwardEntity.builder().awardId(101).awardCount(1).awardSurplus(1)
                    .awardRate(new BigDecimal("0.5")).build(),
            StrategyAwardEntity.builder().awardId(106).awardCount(-1).awardSurplus(-1)
                    .awardRate(new BigDecimal("0.5")).build());

    @Test
    void configuredUnlimitedAwardIsTheOnlyFallback() {
        assertEquals(106, FallbackAwardPolicy.requireValid("{\"award_id\":106}", awards));
    }

    @Test
    void finiteOrMissingFallbackCannotBePublished() {
        assertThrows(IllegalArgumentException.class,
                () -> FallbackAwardPolicy.requireValid("{\"award_id\":101}", awards));
        assertThrows(IllegalArgumentException.class,
                () -> FallbackAwardPolicy.requireValid("{\"award_id\":999}", awards));
        assertThrows(IllegalArgumentException.class,
                () -> FallbackAwardPolicy.requireValid(null, awards));
        assertThrows(IllegalArgumentException.class,
                () -> FallbackAwardPolicy.requireValid("{\"award_id\":106.5}", awards));
    }
}
