package shirlin.ai.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import shirlin.ai.infrastructure.dao.po.InventoryReservation;

@Mapper
public interface IInventoryBucketDao {
    Integer countBuckets(@Param("inventoryType") String inventoryType,
                         @Param("inventoryKey") String inventoryKey);
    int reserveBucket(@Param("inventoryType") String inventoryType,
                      @Param("inventoryKey") String inventoryKey,
                      @Param("bucketId") Integer bucketId);
    int restoreBucket(@Param("inventoryType") String inventoryType,
                      @Param("inventoryKey") String inventoryKey,
                      @Param("bucketId") Integer bucketId);
    int insertReservation(InventoryReservation reservation);
    InventoryReservation selectReservation(@Param("reservationId") String reservationId);
    InventoryReservation selectReservationForUpdate(@Param("reservationId") String reservationId);
    int markReleased(@Param("reservationId") String reservationId);
}
