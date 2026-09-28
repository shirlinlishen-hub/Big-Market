-- Apply once after 2026-09-24-inventory-bucket-outbox.sql.
-- The guards make schema changes and pending-task backfill safe to rerun.

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN table_name_value varchar(64),
    IN column_name_value varchar(64),
    IN ddl_value text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl_statement = ddl_value;
        PREPARE migration_statement FROM @ddl_statement;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing(
    'outbox_event', 'locked_by',
    'ALTER TABLE outbox_event ADD COLUMN locked_by varchar(64) DEFAULT NULL'
);
CALL add_column_if_missing(
    'outbox_event', 'lock_token',
    'ALTER TABLE outbox_event ADD COLUMN lock_token varchar(64) DEFAULT NULL'
);
CALL add_column_if_missing(
    'outbox_event', 'locked_until',
    'ALTER TABLE outbox_event ADD COLUMN locked_until datetime DEFAULT NULL'
);
CALL add_column_if_missing(
    'outbox_event', 'published_time',
    'ALTER TABLE outbox_event ADD COLUMN published_time datetime DEFAULT NULL'
);
CALL add_column_if_missing(
    'outbox_event', 'dead_time',
    'ALTER TABLE outbox_event ADD COLUMN dead_time datetime DEFAULT NULL'
);
CALL add_column_if_missing(
    'award_delivery_task', 'dispatch_version',
    'ALTER TABLE award_delivery_task ADD COLUMN dispatch_version int NOT NULL DEFAULT 0'
);

DROP PROCEDURE add_column_if_missing;

DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(
    IN table_name_value varchar(64),
    IN index_name_value varchar(64),
    IN ddl_value text
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl_statement = ddl_value;
        PREPARE migration_statement FROM @ddl_statement;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing(
    'outbox_event', 'idx_outbox_claim',
    'ALTER TABLE outbox_event ADD KEY idx_outbox_claim (event_type, status, next_attempt_at, locked_until, create_time)'
);

DROP PROCEDURE add_index_if_missing;

ALTER TABLE outbox_event
    MODIFY COLUMN status tinyint NOT NULL DEFAULT 0
        COMMENT '0-pending 1-published 2-dead';

CREATE TABLE IF NOT EXISTS inbox_event (
    consumer_name varchar(64) NOT NULL,
    event_id varchar(64) NOT NULL,
    event_key varchar(128) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    payload_hash char(64) NOT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-processing 1-success',
    duplicate_count int NOT NULL DEFAULT 0,
    first_received_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_received_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_time datetime DEFAULT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_event_key (event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backfill only initial dispatches for tasks that can still be delivered.
-- The deterministic event ID and unique event_key make this statement rerunnable.
INSERT INTO outbox_event
    (event_id, event_type, event_key, aggregate_id, partition_key, payload, status)
SELECT CONCAT('bf-', LEFT(SHA2(CONCAT('award-delivery:', task.order_id, ':v0'), 256), 61)),
       'AWARD_DELIVERY_REQUESTED',
       CONCAT('award-delivery:', task.order_id, ':v0'),
       task.order_id,
       task.user_id,
       JSON_OBJECT('orderId', task.order_id, 'userId', task.user_id),
       0
FROM award_delivery_task task
WHERE task.status = 0
  AND task.dispatch_version = 0
  AND NOT EXISTS (
      SELECT 1
      FROM outbox_event event
      WHERE event.event_key = CONCAT('award-delivery:', task.order_id, ':v0')
  );

-- Verification: every query except the status summary must return no rows.

-- Duplicate business event keys.
SELECT event_key, COUNT(*) AS duplicate_count
FROM outbox_event
GROUP BY event_key
HAVING COUNT(*) > 1;

-- Delivery events whose aggregate no longer has a task.
SELECT event.event_id, event.event_key, event.aggregate_id
FROM outbox_event event
LEFT JOIN award_delivery_task task ON task.order_id = event.aggregate_id
WHERE event.event_type = 'AWARD_DELIVERY_REQUESTED'
  AND task.order_id IS NULL;

-- Duplicate points ledgers by order. The primary key should make this empty.
SELECT order_id, COUNT(*) AS duplicate_count
FROM user_points_ledger
GROUP BY order_id
HAVING COUNT(*) > 1;

-- Operational baseline. Review counts instead of expecting an empty result.
SELECT event_type, status, COUNT(*) AS event_count
FROM outbox_event
GROUP BY event_type, status
ORDER BY event_type, status;
