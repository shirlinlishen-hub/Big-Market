-- Run the checks first. Resolve rows before applying either unique key.
SELECT out_business_no, COUNT(*) AS copies
FROM activity_order WHERE out_business_no IS NOT NULL
GROUP BY out_business_no HAVING COUNT(*) > 1;

ALTER TABLE activity_order
    DROP INDEX uk_user_business_no,
    ADD UNIQUE KEY uk_payment_order_no (out_business_no),
    ADD COLUMN grant_total_count int NOT NULL DEFAULT 0 AFTER out_business_no,
    ADD COLUMN grant_month_count int NOT NULL DEFAULT 0 AFTER grant_total_count,
    ADD COLUMN grant_day_count int NOT NULL DEFAULT 0 AFTER grant_month_count,
    ADD COLUMN refund_event_id varchar(64) DEFAULT NULL AFTER grant_day_count,
    ADD COLUMN refund_removed_count int DEFAULT NULL AFTER refund_event_id,
    ADD COLUMN refund_exposure_count int DEFAULT NULL AFTER refund_removed_count,
    ADD COLUMN refund_time datetime DEFAULT NULL AFTER refund_exposure_count,
    ADD UNIQUE KEY uk_refund_event (refund_event_id);

-- Backfill entitlement snapshots for purchase-granted rows created before this migration.
UPDATE activity_order o
JOIN activity_sku s ON s.sku_id = o.sku_id
JOIN activity_count c ON c.activity_count_id = s.activity_count_id
SET o.grant_total_count = c.total_count,
    o.grant_month_count = c.month_count,
    o.grant_day_count = c.day_count
WHERE o.order_status = 3 AND o.grant_total_count = 0;

ALTER TABLE sku_rebate_order
    MODIFY COLUMN status tinyint NOT NULL DEFAULT 0
        COMMENT '0-pending 1-completed 2-cancelled';

CREATE TABLE IF NOT EXISTS payment_event (
    event_id varchar(64) NOT NULL,
    event_type varchar(32) NOT NULL,
    payment_order_no varchar(64) NOT NULL,
    user_id varchar(32) NOT NULL,
    activity_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    payload_hash char(64) NOT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-processing 1-processed 2-manual-review',
    purchase_order_id varchar(64) DEFAULT NULL,
    removed_unused_count int DEFAULT NULL,
    consumed_exposure_count int DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_payment_order (payment_order_no),
    KEY idx_event_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS award_delivery_audit (
    id bigint NOT NULL AUTO_INCREMENT,
    order_id varchar(64) NOT NULL,
    operator_id varchar(64) NOT NULL,
    action varchar(32) NOT NULL,
    note varchar(500) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_delivery_order (order_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
