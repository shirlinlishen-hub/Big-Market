package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.infrastructure.adapter.repository.AwardPayloadResolver;
import shirlin.ai.infrastructure.dao.po.Award;

import static org.junit.jupiter.api.Assertions.*;

class AwardPayloadResolverTest {
    @Test
    void fixedPointsAreSnapshottedAtDrawTime() {
        Award award = Award.builder().awardKey("user_points")
                .awardConfig("{\"points\":100}").build();
        assertEquals("100", AwardPayloadResolver.resolve(award));
    }

    @Test
    void randomPointsStayWithinConfiguredInclusiveBounds() {
        Award award = Award.builder().awardKey("random_points")
                .awardConfig("{\"min\":10,\"max\":12}").build();
        for (int i = 0; i < 100; i++) {
            int value = Integer.parseInt(AwardPayloadResolver.resolve(award));
            assertTrue(value >= 10 && value <= 12);
        }
    }

    @Test
    void invalidPointConfigurationCannotCreateAnAwardTask() {
        Award award = Award.builder().awardKey("user_points")
                .awardConfig("{\"points\":0}").build();
        assertThrows(IllegalArgumentException.class, () -> AwardPayloadResolver.resolve(award));
    }
}
