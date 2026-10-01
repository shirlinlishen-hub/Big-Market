-- Apply once after 2026-09-23-business-entry.sql.
-- MySQL is the only inventory authority. Redis inventory keys are no longer used.

CREATE TABLE IF NOT EXISTS inventory_stock_bucket (
    inventory_type varchar(16) NOT NULL COMMENT 'SKU or AWARD',
    inventory_key varchar(64) NOT NULL COMMENT 'skuId or strategyId:awardId',
    bucket_id smallint NOT NULL,
    stock_count bigint NOT NULL,
    stock_surplus bigint NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inventory_type, inventory_key, bucket_id),
    CONSTRAINT chk_inventory_bucket_stock CHECK
        (stock_count >= 0 AND stock_surplus >= 0 AND stock_surplus <= stock_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_reservation (
    reservation_id varchar(128) NOT NULL,
    inventory_type varchar(16) NOT NULL,
    inventory_key varchar(64) NOT NULL,
    bucket_id smallint NOT NULL COMMENT '-1 means unlimited inventory',
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-reserved/consumed 1-released',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (reservation_id),
    KEY idx_inventory_reservation (inventory_type, inventory_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS outbox_event (
    event_id varchar(64) NOT NULL,
    event_type varchar(64) NOT NULL,
    event_key varchar(128) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    partition_key varchar(64) NOT NULL,
    payload json NOT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-pending 1-published',
    attempts int NOT NULL DEFAULT 0,
    next_attempt_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error varchar(500) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_outbox_event_key (event_key),
    KEY idx_outbox_pending (status, next_attempt_at, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Fixed 16 buckets. Change this only through a controlled inventory rebalance migration.
CREATE TEMPORARY TABLE tmp_inventory_bucket_id (bucket_id smallint PRIMARY KEY);
INSERT INTO tmp_inventory_bucket_id(bucket_id)
VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15);

INSERT IGNORE INTO inventory_stock_bucket
    (inventory_type, inventory_key, bucket_id, stock_count, stock_surplus)
SELECT 'SKU', CAST(sku_id AS CHAR), b.bucket_id,
       stock_count DIV 16 + IF(b.bucket_id < MOD(stock_count, 16), 1, 0),
       stock_surplus DIV 16 + IF(b.bucket_id < MOD(stock_surplus, 16), 1, 0)
FROM activity_sku
CROSS JOIN tmp_inventory_bucket_id b
WHERE stock_count >= 0 AND stock_surplus >= 0;

INSERT IGNORE INTO inventory_stock_bucket
    (inventory_type, inventory_key, bucket_id, stock_count, stock_surplus)
SELECT 'AWARD', CONCAT(strategy_id, ':', award_id), b.bucket_id,
       award_count DIV 16 + IF(b.bucket_id < MOD(award_count, 16), 1, 0),
       award_surplus DIV 16 + IF(b.bucket_id < MOD(award_surplus, 16), 1, 0)
FROM strategy_award
CROSS JOIN tmp_inventory_bucket_id b
WHERE award_count >= 0 AND award_surplus >= 0;

DROP TEMPORARY TABLE tmp_inventory_bucket_id;

-- Verify totals before enabling the new code. Both queries must return no rows.
SELECT s.sku_id, s.stock_surplus, COALESCE(SUM(b.stock_surplus), 0) AS bucket_surplus
FROM activity_sku s
LEFT JOIN inventory_stock_bucket b
  ON b.inventory_type = 'SKU' AND b.inventory_key = CAST(s.sku_id AS CHAR)
WHERE s.stock_count >= 0
GROUP BY s.sku_id, s.stock_surplus
HAVING s.stock_surplus <> bucket_surplus;

SELECT a.strategy_id, a.award_id, a.award_surplus,
       COALESCE(SUM(b.stock_surplus), 0) AS bucket_surplus
FROM strategy_award a
LEFT JOIN inventory_stock_bucket b
  ON b.inventory_type = 'AWARD'
 AND b.inventory_key = CONCAT(a.strategy_id, ':', a.award_id)
WHERE a.award_count >= 0
GROUP BY a.strategy_id, a.award_id, a.award_surplus
HAVING a.award_surplus <> bucket_surplus;
