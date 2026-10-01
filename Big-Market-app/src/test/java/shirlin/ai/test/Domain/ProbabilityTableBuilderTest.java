package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.entity.AwardRateRange;
import shirlin.ai.domain.strategy.service.Armory.ProbabilityTableBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProbabilityTableBuilderTest {
    @Test
    void everyTicketBelongsToExactlyOneHalfOpenRange() {
        List<AwardRateRange> ranges = ProbabilityTableBuilder.build(
                Map.of(101, new BigDecimal("0.0001"), 102, new BigDecimal("0.9999")),
                10000, true);
        assertEquals(0, ranges.get(0).getRangeStart());
        assertEquals(1, ranges.get(0).getRangeEnd());
        assertEquals(1, ranges.get(1).getRangeStart());
        assertEquals(10000, ranges.get(1).getRangeEnd());
        int first = 0;
        for (int ticket = 0; ticket < 10000; ticket++) {
            if (ProbabilityTableBuilder.pick(ranges, ticket) == 101) first++;
        }
        assertEquals(1, first);
    }

    @Test
    void zeroProbabilityAwardCannotWin() {
        List<AwardRateRange> ranges = ProbabilityTableBuilder.build(
                Map.of(101, BigDecimal.ZERO, 102, BigDecimal.ONE), 100, true);
        assertEquals(1, ranges.size());
        for (int ticket = 0; ticket < 100; ticket++) {
            assertEquals(102, ProbabilityTableBuilder.pick(ranges, ticket));
        }
    }

    @Test
    void invalidOrUnrepresentablePoolIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProbabilityTableBuilder.build(
                Map.of(101, new BigDecimal("0.4")), 100, true));
        assertThrows(IllegalArgumentException.class, () -> ProbabilityTableBuilder.build(
                Map.of(101, new BigDecimal("0.333"), 102, new BigDecimal("0.667")), 100, true));
        assertThrows(IllegalArgumentException.class, () -> ProbabilityTableBuilder.build(
                Map.of(101, BigDecimal.ZERO), 100, false));
    }
}
