package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.infrastructure.adapter.repository.DrawRecoveryService;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.IDrawOrderDao;
import shirlin.ai.infrastructure.dao.IUsagePeriodDao;
import shirlin.ai.infrastructure.dao.po.DrawOrder;

import java.sql.Date;

import static org.mockito.Mockito.*;

class DrawRecoveryServiceTest {
    @Test
    void timedOutDrawRestoresQuotaOnlyOnce() {
        IDrawOrderDao orders = mock(IDrawOrderDao.class);
        IActivityAccountDao accounts = mock(IActivityAccountDao.class);
        IUsagePeriodDao periods = mock(IUsagePeriodDao.class);
        DrawRecoveryService service = new DrawRecoveryService();
        ReflectionTestUtils.setField(service, "orderDao", orders);
        ReflectionTestUtils.setField(service, "accountDao", accounts);
        ReflectionTestUtils.setField(service, "periodDao", periods);
        DrawOrder order = new DrawOrder();
        order.setOrderId("draw-1"); order.setUserId("user-1");
        order.setActivityId(20001L); order.setStatus(0);
        order.setDrawDay(Date.valueOf("2026-09-22")); order.setDrawMonth("2026-09");
        when(orders.selectForUpdate("user-1", "draw-1")).thenReturn(order);
        when(orders.cancel("user-1", "draw-1")).thenReturn(1);
        when(accounts.restoreTotalSurplus("user-1", 20001L)).thenReturn(1);
        when(periods.decrementIfPositive(any(), any(), any(), any())).thenReturn(1);

        service.cancelTimedOut("user-1", "draw-1");
        order.setStatus(2);
        service.cancelTimedOut("user-1", "draw-1");

        verify(accounts, times(1)).restoreTotalSurplus("user-1", 20001L);
        verify(periods, times(2)).decrementIfPositive(any(), any(), any(), any());
    }
}
