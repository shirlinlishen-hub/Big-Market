package shirlin.ai.infrastructure.dao.po;

import lombok.Data;

@Data
public class InventoryReservation {
    private String reservationId;
    private String inventoryType;
    private String inventoryKey;
    private Integer bucketId;
    /** 0 reserved/consumed, 1 released. */
    private Integer status;
}
