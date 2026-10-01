package shirlin.ai.test.Trigger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.api.dto.RaffleRequestDTO;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.trigger.http.RaffleController;
import shirlin.ai.trigger.security.JwtIdentity;
import shirlin.ai.trigger.security.JwtIdentityVerifier;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RaffleControllerTest {
    @Test
    void verifiedJwtSubjectAndIdempotencyHeaderBuildDrawRequest() {
        JwtIdentityVerifier identities = mock(JwtIdentityVerifier.class);
        shirlin.ai.domain.strategy.service.IRaffleService draws =
                mock(shirlin.ai.domain.strategy.service.IRaffleService.class);
        RaffleController controller = new RaffleController();
        ReflectionTestUtils.setField(controller, "identityVerifier", identities);
        ReflectionTestUtils.setField(controller, "raffleService", draws);
        when(identities.verify("Bearer token")).thenReturn(new JwtIdentity("user-1", Set.of()));
        when(draws.doRaffle(any())).thenReturn(RaffleResultEntity.builder().awardId(106).build());
        RaffleRequestDTO request = new RaffleRequestDTO(); request.setActivityId(20001L);

        controller.draw("Bearer token", "request-123", request);

        ArgumentCaptor<ActivityFactorEntity> factor = ArgumentCaptor.forClass(ActivityFactorEntity.class);
        verify(draws).doRaffle(factor.capture());
        assertEquals("user-1", factor.getValue().getUserId());
        assertEquals("request-123", factor.getValue().getOutBusinessNo());
        assertEquals(null, factor.getValue().getStrategyId());
    }

    @Test
    void shortIdempotencyKeyIsRejectedBeforeDraw() {
        JwtIdentityVerifier identities = mock(JwtIdentityVerifier.class);
        shirlin.ai.domain.strategy.service.IRaffleService draws =
                mock(shirlin.ai.domain.strategy.service.IRaffleService.class);
        RaffleController controller = new RaffleController();
        ReflectionTestUtils.setField(controller, "identityVerifier", identities);
        ReflectionTestUtils.setField(controller, "raffleService", draws);
        when(identities.verify(anyString())).thenReturn(new JwtIdentity("user-1", Set.of()));
        RaffleRequestDTO request = new RaffleRequestDTO(); request.setActivityId(20001L);

        assertThrows(IllegalArgumentException.class,
                () -> controller.draw("Bearer token", "short", request));
        verifyNoInteractions(draws);
    }
}
