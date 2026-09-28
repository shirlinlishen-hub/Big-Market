package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.strategy.model.valobj.FulfillmentResult;
import shirlin.ai.infrastructure.dao.IUserPointsDao;
import shirlin.ai.infrastructure.dao.po.AwardDeliveryTask;
import shirlin.ai.infrastructure.dao.po.UserPointsLedger;
import shirlin.ai.infrastructure.delivery.PointsAwardFulfillmentHandler;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;
import shirlin.ai.infrastructure.messaging.exception.RetryableDeliveryException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointsAwardFulfillmentHandlerTest {

    @Test
    void creditsFixedPointsWhenLedgerDoesNotExist() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.insertLedger("order-1", "user-1", 100)).thenReturn(1);
        when(points.addPoints("user-1", 100)).thenReturn(1);
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertEquals(FulfillmentResult.SUCCESS,
                handler.fulfill(task("user_points", "100")));

        verify(points).insertLedger("order-1", "user-1", 100);
        verify(points).addPoints("user-1", 100);
    }

    @Test
    void creditsFrozenRandomPointsAndAcceptsPositiveUpdateCount() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.insertLedger("order-1", "user-1", 37)).thenReturn(1);
        when(points.addPoints("user-1", 37)).thenReturn(2);
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertEquals(FulfillmentResult.SUCCESS,
                handler.fulfill(task("random_points", "37")));

        verify(points).addPoints("user-1", 37);
    }

    @Test
    void matchingLedgerMakesReplayACommittedNoOp() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.selectLedgerForUpdate("order-1"))
                .thenReturn(ledger("user-1", 100));
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertEquals(FulfillmentResult.SUCCESS,
                handler.fulfill(task("user_points", "100")));

        verify(points, never()).insertLedger(anyString(), anyString(), anyInt());
        verify(points, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void nonPositivePointsArePermanentMessageFailure() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertThrows(NonRetryableDeliveryException.class,
                () -> handler.fulfill(task("user_points", "0")));

        verifyNoInteractions(points);
    }

    @Test
    void ledgerForDifferentUserRequiresManualReviewWithoutCrediting() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.selectLedgerForUpdate("order-1"))
                .thenReturn(ledger("another-user", 100));
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertEquals(FulfillmentResult.MANUAL,
                handler.fulfill(task("user_points", "100")));

        verify(points, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void ledgerForDifferentAmountRequiresManualReviewWithoutCrediting() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.selectLedgerForUpdate("order-1"))
                .thenReturn(ledger("user-1", 99));
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertEquals(FulfillmentResult.MANUAL,
                handler.fulfill(task("user_points", "100")));

        verify(points, never()).addPoints(anyString(), anyInt());
    }

    @Test
    void ledgerInsertRaceIsRetryable() {
        IUserPointsDao points = mock(IUserPointsDao.class);
        when(points.insertLedger("order-1", "user-1", 100)).thenReturn(0);
        PointsAwardFulfillmentHandler handler = new PointsAwardFulfillmentHandler(points);

        assertThrows(RetryableDeliveryException.class,
                () -> handler.fulfill(task("user_points", "100")));

        verify(points, never()).addPoints(anyString(), anyInt());
    }

    private static AwardDeliveryTask task(String awardKey, String awardValue) {
        AwardDeliveryTask task = new AwardDeliveryTask();
        task.setOrderId("order-1");
        task.setUserId("user-1");
        task.setAwardKey(awardKey);
        task.setAwardValue(awardValue);
        return task;
    }

    private static UserPointsLedger ledger(String userId, int points) {
        UserPointsLedger ledger = new UserPointsLedger();
        ledger.setOrderId("order-1");
        ledger.setUserId(userId);
        ledger.setPoints(points);
        return ledger;
    }
}
