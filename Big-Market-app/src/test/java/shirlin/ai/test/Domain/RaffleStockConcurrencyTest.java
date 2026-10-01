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
import shirlin.ai.infrastructure.dao.po.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RaffleStockConcurrencyTest {
    @Test
    void concurrentConditionalStockWinsNeverExceedAvailableUnits() throws Exception {
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
        when(orders.selectForUpdate(eq("user-1"), anyString())).thenAnswer(call -> {
            DrawOrder order = new DrawOrder();
            order.setOrderId(call.getArgument(1)); order.setUserId("user-1");
            order.setStrategyId(1L); order.setStatus(0);
            return order;
        });
        when(orders.complete(eq("user-1"), anyString(), anyInt(), anyInt())).thenReturn(1);
        when(strategies.queryFallbackAwardId(1L)).thenReturn(106);
        StrategyAward limited = new StrategyAward();
        limited.setAwardId(101); limited.setAwardType(1);
        limited.setAwardCount(5); limited.setAwardSurplus(5);
        StrategyAward fallback = new StrategyAward();
        fallback.setAwardId(106); fallback.setAwardType(1);
        fallback.setAwardCount(-1); fallback.setAwardSurplus(-1);
        when(stock.selectByStrategyIdAndAwardId(1L, 101)).thenReturn(limited);
        when(stock.selectByStrategyIdAndAwardId(1L, 106)).thenReturn(fallback);
        AtomicInteger remaining = new AtomicInteger(5);
        when(inventory.reserveAward(eq(1L), eq(101), anyString(), eq(5))).thenAnswer(call -> {
            while (true) {
                int current = remaining.get();
                if (current == 0) return false;
                if (remaining.compareAndSet(current, current - 1)) return true;
            }
        });
        when(inventory.reserveAward(eq(1L), eq(106), anyString(), eq(-1))).thenReturn(true);
        when(awards.selectByAwardId(anyInt())).thenAnswer(call -> Award.builder()
                .awardId(call.getArgument(0)).awardKey("user_points")
                .awardConfig("{\"points\":5}").build());

        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 50; i++) {
                final int orderNo = i;
                results.add(pool.submit(() -> {
                    start.await();
                    return repository.completeDraw(DrawOrderEntity.builder()
                                    .userId("user-1").orderId("draw-" + orderNo).build(),
                            RaffleResultEntity.builder().awardId(101).build()).getAwardId();
                }));
            }
            start.countDown();
            int limitedWins = 0;
            for (Future<Integer> result : results) if (result.get(15, TimeUnit.SECONDS) == 101) limitedWins++;
            assertEquals(5, limitedWins);
            assertEquals(0, remaining.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
