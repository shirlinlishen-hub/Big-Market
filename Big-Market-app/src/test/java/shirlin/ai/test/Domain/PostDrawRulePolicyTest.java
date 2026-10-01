package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;
import shirlin.ai.domain.strategy.service.Rule.PostDrawRulePolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostDrawRulePolicyTest {
    @Test
    void lockedPrizeBecomesExplicitFallback() {
        RuleLockConfigEntity lock = RuleLockConfigEntity.builder()
                .unlockCount(10).lockedAwardIds(List.of(101)).build();
        assertEquals(106, PostDrawRulePolicy.choose(101, 106, lock, 9, null, 0));
        assertEquals(101, PostDrawRulePolicy.choose(101, 106, lock, 10, null, 0));
    }

    @Test
    void fiftiethDrawForcesLuckAwardAndResetsLuck() {
        RuleLuckConfigEntity luck = RuleLuckConfigEntity.builder()
                .luckThreshold(50).luckAwardId(106).luckIncrement(1).build();
        assertEquals(101, PostDrawRulePolicy.choose(101, 106, null, 0, luck, 48));
        assertEquals(106, PostDrawRulePolicy.choose(101, 106, null, 0, luck, 49));
        assertEquals(49, PostDrawRulePolicy.nextLuck(101, luck, 48));
        assertEquals(0, PostDrawRulePolicy.nextLuck(106, luck, 49));
    }
}
