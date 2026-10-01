package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.domain.strategy.service.IRaffleStrategy;
import shirlin.ai.domain.strategy.service.Raffle.RaffleOrderService;
import shirlin.ai.domain.strategy.service.Raffle.RaffleService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class RaffleReplayTest {
    @Test
    void completedRequestReturnsStoredAwardWithoutDrawingAgain() {
        RaffleService service = new RaffleService();
        RaffleOrderService orders = mock(RaffleOrderService.class);
        IRaffleStrategy strategy = mock(IRaffleStrategy.class);
        IActivityRepository activities = mock(IActivityRepository.class);
        ReflectionTestUtils.setField(service, "raffleOrderService", orders);
        ReflectionTestUtils.setField(service, "raffleStrategy", strategy);
        ReflectionTestUtils.setField(service, "activityRepository", activities);
        ActivityFactorEntity factor = ActivityFactorEntity.builder()
                .userId("buyer-1").activityId(20001L).strategyId(100001L)
                .outBusinessNo("draw-123").build();
        when(activities.queryActivityById(20001L)).thenReturn(ActivityEntity.builder()
                .activityId(20001L).strategyId(100001L).status(1).build());
        when(orders.reserveDraw(factor)).thenReturn(DrawOrderEntity.builder()
                .orderId("draw-order-1").status(1).awardId(103).awardType(1).build());

        RaffleResultEntity result = service.doRaffle(factor);

        assertEquals(103, result.getAwardId());
        verifyNoInteractions(strategy);
    }
}
