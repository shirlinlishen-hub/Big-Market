package shirlin.ai.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import shirlin.ai.infrastructure.dao.IInventoryBucketDao;
import shirlin.ai.infrastructure.dao.po.InventoryReservation;

@Service
public class MysqlInventoryBucketService {
    private static final String SKU = "SKU";
    private static final String AWARD = "AWARD";

    @Resource private IInventoryBucketDao bucketDao;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveSku(Long skuId, String paymentOrderNo, Long configuredStock) {
        return reserve(SKU, String.valueOf(skuId), "SKU_PURCHASE:" + paymentOrderNo,
                configuredStock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveAward(Long strategyId, Integer awardId, String raffleOrderId,
                                Integer configuredStock) {
        return reserve(AWARD, strategyId + ":" + awardId, "AWARD:" + raffleOrderId,
                configuredStock == null ? null : configuredStock.longValue());
    }

    private boolean reserve(String type, String key, String reservationId, Long configuredStock) {
        if (configuredStock == null || configuredStock < -1) {
            throw new IllegalArgumentException("Inventory configuration is invalid");
        }
        InventoryReservation existing = bucketDao.selectReservation(reservationId);
        if (existing != null) return existing.getStatus() != null && existing.getStatus() == 0;
        if (configuredStock == -1L) {
            insertReservation(reservationId, type, key, -1);
            return true;
        }
        Integer count = bucketDao.countBuckets(type, key);
        if (count == null || count <= 0) {
            throw new IllegalStateException("Inventory buckets are not initialized for " + type + ":" + key);
        }
        int start = Math.floorMod(reservationId.hashCode(), count);
        for (int offset = 0; offset < count; offset++) {
            int bucketId = (start + offset) % count;
            if (bucketDao.reserveBucket(type, key, bucketId) == 1) {
                insertReservation(reservationId, type, key, bucketId);
                return true;
            }
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseSku(String paymentOrderNo) {
        String reservationId = "SKU_PURCHASE:" + paymentOrderNo;
        InventoryReservation reservation = bucketDao.selectReservationForUpdate(reservationId);
        if (reservation == null || Integer.valueOf(1).equals(reservation.getStatus())) return;
        if (!SKU.equals(reservation.getInventoryType())) {
            throw new IllegalStateException("Reservation is not a SKU reservation");
        }
        if (reservation.getBucketId() != null && reservation.getBucketId() >= 0
                && bucketDao.restoreBucket(SKU, reservation.getInventoryKey(),
                reservation.getBucketId()) != 1) {
            throw new IllegalStateException("Failed to restore inventory bucket");
        }
        if (bucketDao.markReleased(reservationId) != 1) {
            throw new IllegalStateException("Inventory reservation state changed");
        }
    }

    private void insertReservation(String reservationId, String type, String key, int bucketId) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId(reservationId); reservation.setInventoryType(type);
        reservation.setInventoryKey(key); reservation.setBucketId(bucketId); reservation.setStatus(0);
        if (bucketDao.insertReservation(reservation) != 1) {
            throw new IllegalStateException("Failed to persist inventory reservation");
        }
    }
}
