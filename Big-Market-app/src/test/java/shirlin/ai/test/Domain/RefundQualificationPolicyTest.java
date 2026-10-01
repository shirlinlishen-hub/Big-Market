package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.Activity.service.RefundQualificationPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundQualificationPolicyTest {
    @Test
    void refundRemovesOnlyUnusedRightsAndReportsConsumedExposure() {
        RefundQualificationPolicy.Decision decision =
                RefundQualificationPolicy.decide(5, 2, 3);

        assertEquals(7, decision.grantedCount());
        assertEquals(3, decision.removedUnusedCount());
        assertEquals(4, decision.consumedExposureCount());
    }

    @Test
    void unlimitedGrantRequiresManualReview() {
        assertThrows(IllegalArgumentException.class,
                () -> RefundQualificationPolicy.decide(-1, 0, 10));
    }
}
