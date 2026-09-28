package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.infrastructure.adapter.repository.AwardDeliveryService;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AwardDeliveryOperationsTest {
    @Test
    void operatorCanRequeueManualOrFailedTaskWithAudit() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IAwardDeliveryAuditDao audits = mock(IAwardDeliveryAuditDao.class);
        AwardDeliveryService service = service(tasks, audits, mock(IUserAwardRecordDao.class));
        AwardDeliveryTask task = task("order-1", "user-1", 3);
        when(tasks.selectByOrderIdForUpdate("order-1")).thenReturn(task);
        when(tasks.requeue("order-1")).thenReturn(1);
        when(audits.insert("order-1", "operator-1", "RETRY", "provider recovered")).thenReturn(1);

        service.retry("order-1", "operator-1", "provider recovered");

        verify(tasks).requeue("order-1");
        verify(audits).insert("order-1", "operator-1", "RETRY", "provider recovered");
    }

    @Test
    void operatorCanMarkManualTaskSuccessfulAndUserCanQueryIt() {
        IAwardDeliveryTaskDao tasks = mock(IAwardDeliveryTaskDao.class);
        IAwardDeliveryAuditDao audits = mock(IAwardDeliveryAuditDao.class);
        IUserAwardRecordDao awards = mock(IUserAwardRecordDao.class);
        AwardDeliveryService service = service(tasks, audits, awards);
        AwardDeliveryTask task = task("order-1", "user-1", 3);
        when(tasks.selectByOrderIdForUpdate("order-1")).thenReturn(task);
        when(tasks.markManualSuccess("order-1")).thenReturn(1);
        when(awards.updateAwardStateByOrder("user-1", "order-1", 2)).thenReturn(1);
        when(audits.insert("order-1", "operator-1", "MANUAL_SUCCESS", "tracking confirmed")).thenReturn(1);
        when(tasks.selectByUserAndOrder("user-1", "order-1")).thenReturn(task);

        service.completeManual("order-1", "operator-1", true, "tracking confirmed");

        assertEquals("order-1", service.queryUserTask("user-1", "order-1").getOrderId());
        verify(awards).updateAwardStateByOrder("user-1", "order-1", 2);
    }

    private AwardDeliveryService service(IAwardDeliveryTaskDao tasks,
                                         IAwardDeliveryAuditDao audits,
                                         IUserAwardRecordDao awards) {
        AwardDeliveryService service = new AwardDeliveryService();
        ReflectionTestUtils.setField(service, "taskDao", tasks);
        ReflectionTestUtils.setField(service, "auditDao", audits);
        ReflectionTestUtils.setField(service, "awardRecordDao", awards);
        ReflectionTestUtils.setField(service, "fulfillmentService", mock(AwardFulfillmentService.class));
        return service;
    }

    private AwardDeliveryTask task(String orderId, String userId, int status) {
        AwardDeliveryTask task = new AwardDeliveryTask();
        task.setOrderId(orderId); task.setUserId(userId); task.setStatus(status);
        return task;
    }
}
