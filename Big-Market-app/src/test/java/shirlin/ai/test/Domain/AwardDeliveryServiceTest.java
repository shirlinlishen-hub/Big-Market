package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.adapter.repository.AwardDeliveryService;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentHandler;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;
import shirlin.ai.infrastructure.delivery.ManualAwardFulfillmentHandler;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AwardDeliveryServiceTest {
    @Test
    void localDeliveryDelegatesToSharedFulfillmentCore() throws Exception {
        AwardFulfillmentService fulfillment = mock(AwardFulfillmentService.class);
        when(fulfillment.fulfill("user-1", "draw-1"))
                .thenReturn(FulfillmentResult.SUCCESS);
        AwardDeliveryService service = new AwardDeliveryService();
        inject(service, "fulfillmentService", fulfillment);

        service.deliver("user-1", "draw-1");

        verify(fulfillment).fulfill("user-1", "draw-1");
    }

    @Test
    void sharedCoreCompletesPendingTaskThroughExactlyOneHandler() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IUserAwardRecordDao records = mock(IUserAwardRecordDao.class);
        AwardFulfillmentHandler handler = mock(AwardFulfillmentHandler.class);
        when(handler.supports("user_points")).thenReturn(true);
        when(handler.fulfill(any())).thenReturn(FulfillmentResult.SUCCESS);
        AwardFulfillmentService fulfillment =
                new AwardFulfillmentService(tasks, records, List.of(handler));
        AwardDeliveryTask task = new AwardDeliveryTask();
        task.setOrderId("draw-1"); task.setUserId("user-1");
        task.setAwardKey("user_points"); task.setAwardValue("100");
        task.setStatus(0);
        when(tasks.selectForUpdate("user-1", "draw-1")).thenReturn(task);
        when(tasks.claim("draw-1")).thenReturn(1);
        when(records.updateAwardStateByOrder("user-1", "draw-1", 2)).thenReturn(1);
        when(tasks.markSuccess("draw-1")).thenReturn(1);

        assertEquals(FulfillmentResult.SUCCESS,
                fulfillment.fulfill("user-1", "draw-1"));
        task.setStatus(2);
        assertEquals(FulfillmentResult.SUCCESS,
                fulfillment.fulfill("user-1", "draw-1"));

        verify(handler, times(1)).fulfill(task);
        verify(tasks, times(1)).markSuccess("draw-1");
    }

    @Test
    void manualHandlerOnlyAcceptsConfiguredExternalAwardKeys() {
        ManualAwardFulfillmentHandler handler = new ManualAwardFulfillmentHandler();

        assertTrue(handler.supports("coupon_center"));
        assertTrue(handler.supports("physical_goods"));
        assertFalse(handler.supports("unknown_award"));
    }

    @Test
    void sharedCoreRejectsUnknownAwardKey() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IUserAwardRecordDao records = mock(IUserAwardRecordDao.class);
        AwardFulfillmentService fulfillment =
                new AwardFulfillmentService(tasks, records, List.of());
        AwardDeliveryTask task = new AwardDeliveryTask();
        task.setOrderId("draw-1");
        task.setUserId("user-1");
        task.setAwardKey("unknown_award");
        task.setStatus(0);
        when(tasks.selectForUpdate("user-1", "draw-1")).thenReturn(task);
        when(tasks.claim("draw-1")).thenReturn(1);

        assertThrows(NonRetryableDeliveryException.class,
                () -> fulfillment.fulfill("user-1", "draw-1"));

        verifyNoInteractions(records);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
