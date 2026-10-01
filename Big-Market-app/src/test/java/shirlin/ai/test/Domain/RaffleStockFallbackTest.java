package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.domain.strategy.adapter.repository.IStrategyRepository;
import shirlin.ai.domain.strategy.model.entity.DrawOrderEntity;
import shirlin.ai.domain.strategy.model.entity.RaffleResultEntity;
import shirlin.ai.infrastructure.adapter.repository.RaffleOrderRepository;
import shirlin.ai.infrastructure.adapter.repository.MysqlInventoryBucketService;
import shirlin.ai.infrastructure.adapter.repository.TransactionalOutboxService;
import shirlin.ai.infrastructure.dao.*;
import shirlin.ai.infrastructure.dao.po.Award;
import shirlin.ai.infrastructure.dao.po.DrawOrder;
import shirlin.ai.infrastructure.dao.po.StrategyAward;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RaffleStockFallbackTest {
    @Test
    void missingCandidateIsRejectedInsteadOfSilentlyUsingFallback() {
        RaffleOrderRepository repository = new RaffleOrderRepository();
        IDrawOrderDao orders = mock(IDrawOrderDao.class);
        IStrategyAwardDao stock = mock(IStrategyAwardDao.class);
        IStrategyRepository strategies = mock(IStrategyRepository.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        ReflectionTestUtils.setField(repository, "drawOrderDao", orders);
        ReflectionTestUtils.setField(repository, "strategyAwardDao", stock);
        ReflectionTestUtils.setField(repository, "strategyRepository", strategies);
        ReflectionTestUtils.setField(repository, "strategyRuleDao", mock(IStrategyRuleDao.class));
        ReflectionTestUtils.setField(repository, "inventoryBucketService", inventory);
        DrawOrder order = new DrawOrder();
        order.setOrderId("draw-1"); order.setUserId("user-1");
        order.setStrategyId(1L); order.setStatus(0);
        when(orders.selectForUpdate("user-1", "draw-1")).thenReturn(order);
        when(strategies.queryFallbackAwardId(1L)).thenReturn(106);
        StrategyAward fallback = new StrategyAward();
        fallback.setAwardId(106); fallback.setAwardCount(-1); fallback.setAwardSurplus(-1);
        when(stock.selectByStrategyIdAndAwardId(1L, 106)).thenReturn(fallback);

        assertThrows(IllegalStateException.class, () -> repository.completeDraw(
                DrawOrderEntity.builder().userId("user-1").orderId("draw-1").build(),
                RaffleResultEntity.builder().awardId(999).build()));
        verify(orders, never()).complete(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void exhaustedPrizeCanOnlyBecomeConfiguredUnlimitedFallback() {
        RaffleOrderRepository repository = new RaffleOrderRepository();
        IDrawOrderDao orders = mock(IDrawOrderDao.class);
        IStrategyAwardDao stock = mock(IStrategyAwardDao.class);
        IStrategyRepository strategies = mock(IStrategyRepository.class);
        IAwardDao awards = mock(IAwardDao.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        ReflectionTestUtils.setField(repository, "drawOrderDao", orders);
        ReflectionTestUtils.setField(repository, "strategyAwardDao", stock);
        ReflectionTestUtils.setField(repository, "strategyRepository", strategies);
        ReflectionTestUtils.setField(repository, "strategyRuleDao", mock(IStrategyRuleDao.class));
        ReflectionTestUtils.setField(repository, "awardDao", awards);
        ReflectionTestUtils.setField(repository, "awardRecordDao", mock(IUserAwardRecordDao.class));
        ReflectionTestUtils.setField(repository, "deliveryTaskDao", mock(IAwardDeliveryTaskDao.class));
        ReflectionTestUtils.setField(repository, "inventoryBucketService", inventory);
        ReflectionTestUtils.setField(repository, "outboxService", mock(TransactionalOutboxService.class));
        DrawOrder order = new DrawOrder();
        order.setOrderId("draw-1"); order.setUserId("user-1");
        order.setStrategyId(1L); order.setStatus(0);
        when(orders.selectForUpdate("user-1", "draw-1")).thenReturn(order);
        when(orders.complete("user-1", "draw-1", 106, 1)).thenReturn(1);
        StrategyAward scarce = new StrategyAward();
        scarce.setAwardId(101); scarce.setAwardCount(1); scarce.setAwardSurplus(0);
        scarce.setAwardRate(new BigDecimal("0.9"));
        StrategyAward fallback = new StrategyAward();
        fallback.setAwardId(106); fallback.setAwardType(1);
        fallback.setAwardCount(-1); fallback.setAwardSurplus(-1);
        fallback.setAwardRate(new BigDecimal("0.1"));
        when(stock.selectByStrategyIdAndAwardId(1L, 101)).thenReturn(scarce);
        when(stock.selectByStrategyIdAndAwardId(1L, 106)).thenReturn(fallback);
        when(inventory.reserveAward(1L, 101, "draw-1", 1)).thenReturn(false);
        when(inventory.reserveAward(1L, 106, "draw-1", -1)).thenReturn(true);
        when(strategies.queryFallbackAwardId(1L)).thenReturn(106);
        when(awards.selectByAwardId(106)).thenReturn(Award.builder()
                .awardId(106).awardKey("user_points").awardConfig("{\"points\":5}").build());

        RaffleResultEntity result = repository.completeDraw(
                DrawOrderEntity.builder().userId("user-1").orderId("draw-1").build(),
                RaffleResultEntity.builder().awardId(101).build());

        assertEquals(106, result.getAwardId());
        verify(inventory).reserveAward(1L, 106, "draw-1", -1);
    }
}
