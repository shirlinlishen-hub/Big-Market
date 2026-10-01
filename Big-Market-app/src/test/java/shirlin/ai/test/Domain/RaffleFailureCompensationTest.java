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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RaffleFailureCompensationTest {
    @Test
    void strategyFailureCancelsReservedDrawImmediately() throws Exception {
        RaffleService service = new RaffleService();
        RaffleOrderService orders = mock(RaffleOrderService.class);
        IRaffleStrategy strategy = mock(IRaffleStrategy.class);
        IActivityRepository activities = mock(IActivityRepository.class);
        ReflectionTestUtils.setField(service, "raffleOrderService", orders);
        ReflectionTestUtils.setField(service, "raffleStrategy", strategy);
        ReflectionTestUtils.setField(service, "activityRepository", activities);
        ActivityFactorEntity factor = ActivityFactorEntity.builder()
                .userId("user-1").activityId(20001L).strategyId(1L)
                .outBusinessNo("request-1").build();
        when(activities.queryActivityById(20001L)).thenReturn(ActivityEntity.builder()
                .activityId(20001L).strategyId(1L).status(1).build());
        DrawOrderEntity order = DrawOrderEntity.builder().orderId("draw-1")
                .userId("user-1").status(0).newlyCreated(true).build();
        when(orders.reserveDraw(factor)).thenReturn(order);
        when(strategy.performRaffle(any())).thenThrow(new IllegalStateException("strategy unavailable"));

        assertThrows(RuntimeException.class, () -> service.doRaffle(factor));
        verify(orders).cancelFailedDraw(order);
    }

    @Test
    void resultTransactionFailureAlsoCancelsReservation() throws Exception {
        RaffleService service = new RaffleService();
        RaffleOrderService orders = mock(RaffleOrderService.class);
        IRaffleStrategy strategy = mock(IRaffleStrategy.class);
        IActivityRepository activities = mock(IActivityRepository.class);
        ReflectionTestUtils.setField(service, "raffleOrderService", orders);
        ReflectionTestUtils.setField(service, "raffleStrategy", strategy);
        ReflectionTestUtils.setField(service, "activityRepository", activities);
        ActivityFactorEntity factor = ActivityFactorEntity.builder()
                .userId("user-1").activityId(20001L).strategyId(1L)
                .outBusinessNo("request-2").build();
        when(activities.queryActivityById(20001L)).thenReturn(ActivityEntity.builder()
                .activityId(20001L).strategyId(1L).status(1).build());
        DrawOrderEntity order = DrawOrderEntity.builder().orderId("draw-2")
                .userId("user-1").status(0).newlyCreated(true).build();
        RaffleResultEntity candidate = RaffleResultEntity.builder().awardId(101).build();
        when(orders.reserveDraw(factor)).thenReturn(order);
        when(strategy.performRaffle(any())).thenReturn(candidate);
        when(orders.completeDraw(order, candidate)).thenThrow(new IllegalStateException("DB rollback"));

        assertThrows(RuntimeException.class, () -> service.doRaffle(factor));
        verify(orders).cancelFailedDraw(order);
    }
}
