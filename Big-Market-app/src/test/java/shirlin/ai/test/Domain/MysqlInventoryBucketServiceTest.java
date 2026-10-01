package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import shirlin.ai.infrastructure.adapter.repository.MysqlInventoryBucketService;
import shirlin.ai.infrastructure.dao.IInventoryBucketDao;
import shirlin.ai.infrastructure.dao.po.InventoryReservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MysqlInventoryBucketServiceTest {
    @Test
    void reservationWalksBucketsAndPersistsTheWinningBucket() {
        IInventoryBucketDao dao = mock(IInventoryBucketDao.class);
        MysqlInventoryBucketService service = service(dao);
        when(dao.countBuckets("SKU", "30001")).thenReturn(4);
        when(dao.reserveBucket(eq("SKU"), eq("30001"), anyInt())).thenReturn(0, 1);
        when(dao.insertReservation(any())).thenReturn(1);

        assertTrue(service.reserveSku(30001L, "payment-1", 100L));

        verify(dao, times(2)).reserveBucket(eq("SKU"), eq("30001"), anyInt());
        verify(dao).insertReservation(argThat(r ->
                "SKU_PURCHASE:payment-1".equals(r.getReservationId())
                        && "SKU".equals(r.getInventoryType()) && r.getBucketId() != null));
    }

    @Test
    void exhaustedFiniteInventoryReturnsFalse() {
        IInventoryBucketDao dao = mock(IInventoryBucketDao.class);
        MysqlInventoryBucketService service = service(dao);
        when(dao.countBuckets("AWARD", "1:101")).thenReturn(2);

        assertFalse(service.reserveAward(1L, 101, "draw-1", 10));

        verify(dao, times(2)).reserveBucket(eq("AWARD"), eq("1:101"), anyInt());
        verify(dao, never()).insertReservation(any());
    }

    @Test
    void unlimitedAwardPersistsTraceableReservationWithoutInventoryBucket() {
        IInventoryBucketDao dao = mock(IInventoryBucketDao.class);
        MysqlInventoryBucketService service = service(dao);
        when(dao.insertReservation(any())).thenReturn(1);

        assertTrue(service.reserveAward(1L, 106, "draw-1", -1));

        verify(dao, never()).countBuckets(anyString(), anyString());
        verify(dao, never()).reserveBucket(anyString(), anyString(), anyInt());
        verify(dao).insertReservation(argThat(r ->
                "AWARD:draw-1".equals(r.getReservationId())
                        && "AWARD".equals(r.getInventoryType())
                        && "1:106".equals(r.getInventoryKey())
                        && Integer.valueOf(-1).equals(r.getBucketId())));
    }

    @Test
    void releaseRestoresExactlyTheRecordedBucketOnce() {
        IInventoryBucketDao dao = mock(IInventoryBucketDao.class);
        MysqlInventoryBucketService service = service(dao);
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId("SKU_PURCHASE:payment-1");
        reservation.setInventoryType("SKU"); reservation.setInventoryKey("30001");
        reservation.setBucketId(2); reservation.setStatus(0);
        when(dao.selectReservationForUpdate("SKU_PURCHASE:payment-1")).thenReturn(reservation);
        when(dao.restoreBucket("SKU", "30001", 2)).thenReturn(1);
        when(dao.markReleased("SKU_PURCHASE:payment-1")).thenReturn(1);

        service.releaseSku("payment-1");

        verify(dao).restoreBucket("SKU", "30001", 2);
        verify(dao).markReleased("SKU_PURCHASE:payment-1");
    }

    private MysqlInventoryBucketService service(IInventoryBucketDao dao) {
        MysqlInventoryBucketService service = new MysqlInventoryBucketService();
        ReflectionTestUtils.setField(service, "bucketDao", dao);
        return service;
    }
}
