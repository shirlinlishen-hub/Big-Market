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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RafflePostRuleTransactionTest {
    @Test
    void luckThresholdOverridesCandidateInsideCompletionAndResetsValue() {
        RaffleOrderRepository repository = new RaffleOrderRepository();
        IDrawOrderDao orders = mock(IDrawOrderDao.class);
        IStrategyAwardDao stock = mock(IStrategyAwardDao.class);
        IStrategyRepository strategies = mock(IStrategyRepository.class);
        IStrategyRuleDao rules = mock(IStrategyRuleDao.class);
        IUserLuckAccountDao luckAccounts = mock(IUserLuckAccountDao.class);
        IAwardDao awards = mock(IAwardDao.class);
        MysqlInventoryBucketService inventory = mock(MysqlInventoryBucketService.class);
        TransactionalOutboxService outbox = mock(TransactionalOutboxService.class);
        ReflectionTestUtils.setField(repository, "drawOrderDao", orders);
        ReflectionTestUtils.setField(repository, "strategyAwardDao", stock);
        ReflectionTestUtils.setField(repository, "strategyRepository", strategies);
        ReflectionTestUtils.setField(repository, "strategyRuleDao", rules);
        ReflectionTestUtils.setField(repository, "luckAccountDao", luckAccounts);
        ReflectionTestUtils.setField(repository, "awardDao", awards);
        ReflectionTestUtils.setField(repository, "awardRecordDao", mock(IUserAwardRecordDao.class));
        ReflectionTestUtils.setField(repository, "deliveryTaskDao", mock(IAwardDeliveryTaskDao.class));
        ReflectionTestUtils.setField(repository, "inventoryBucketService", inventory);
        ReflectionTestUtils.setField(repository, "outboxService", outbox);
        DrawOrder order = new DrawOrder();
        order.setOrderId("draw-1"); order.setUserId("user-1");
        order.setStrategyId(1L); order.setStatus(0);
        when(orders.selectForUpdate("user-1", "draw-1")).thenReturn(order);
        when(orders.complete("user-1", "draw-1", 106, 1)).thenReturn(1);
        when(strategies.queryFallbackAwardId(1L)).thenReturn(106);
        StrategyRule luckRule = new StrategyRule();
        luckRule.setRuleValue("{\"luck_threshold\":50,\"luck_award_id\":106,\"luck_increment\":1}");
        when(rules.selectByStrategyIdAndRuleModel(1L, "rule_luck")).thenReturn(luckRule);
        UserLuckAccount luck = new UserLuckAccount();
        luck.setLuckValue(49);
        when(luckAccounts.selectForUpdate("user-1", 1L)).thenReturn(luck);
        when(luckAccounts.setLuckValue("user-1", 1L, 0)).thenReturn(1);
        StrategyAward fallback = new StrategyAward();
        fallback.setAwardId(106); fallback.setAwardType(1);
        fallback.setAwardCount(-1); fallback.setAwardSurplus(-1);
        when(stock.selectByStrategyIdAndAwardId(1L, 106)).thenReturn(fallback);
        when(inventory.reserveAward(1L, 106, "draw-1", -1)).thenReturn(true);
        when(awards.selectByAwardId(106)).thenReturn(Award.builder()
                .awardId(106).awardKey("user_points").awardConfig("{\"points\":5}").build());

        RaffleResultEntity result = repository.completeDraw(
                DrawOrderEntity.builder().userId("user-1").orderId("draw-1").build(),
                RaffleResultEntity.builder().awardId(101).build());

        assertEquals(106, result.getAwardId());
        verify(inventory, never()).reserveAward(eq(1L), eq(101), anyString(), anyInt());
        verify(inventory).reserveAward(1L, 106, "draw-1", -1);
        verify(luckAccounts).setLuckValue("user-1", 1L, 0);
        verify(outbox).append("AWARD_DELIVERY_REQUESTED", "award-delivery:draw-1:v0",
                "draw-1", "user-1", Map.of("orderId", "draw-1", "userId", "user-1"));
    }
}
