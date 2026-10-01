package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.infrastructure.adapter.repository.SkuRebateService;
import shirlin.ai.infrastructure.dao.IActivityAccountDao;
import shirlin.ai.infrastructure.dao.ISkuRebateDao;
import shirlin.ai.infrastructure.dao.po.SkuRebateOrder;

import static org.mockito.Mockito.*;

class SkuRebateServiceTest {
    @Test
    void repeatedRebateGrantsTotalCountOnce() {
        ISkuRebateDao orders = mock(ISkuRebateDao.class);
        IActivityAccountDao accounts = mock(IActivityAccountDao.class);
        SkuRebateService service = new SkuRebateService();
        ReflectionTestUtils.setField(service, "rebateDao", orders);
        ReflectionTestUtils.setField(service, "accountDao", accounts);
        SkuRebateOrder order = new SkuRebateOrder();
        order.setPurchaseOrderId("purchase-1");
        order.setUserId("user-1");
        order.setActivityId(20001L);
        order.setRebateDrawCount(2);
        order.setStatus(0);
        when(orders.selectForUpdate("purchase-1")).thenReturn(order);
        when(accounts.grantTotalOnly("user-1", 20001L, 2)).thenReturn(1);
        when(orders.complete("purchase-1")).thenReturn(1);

        service.grant("purchase-1");
        order.setStatus(1);
        service.grant("purchase-1");

        verify(accounts, times(1)).grantTotalOnly("user-1", 20001L, 2);
        verify(orders, times(1)).complete("purchase-1");
    }
}
