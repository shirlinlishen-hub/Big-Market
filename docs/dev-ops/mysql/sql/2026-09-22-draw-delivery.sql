-- Single-database migration. Apply before enabling the draw endpoint.
CREATE TABLE IF NOT EXISTS activity_usage_period (
    user_id varchar(32) NOT NULL,
    activity_id bigint NOT NULL,
    period_type char(1) NOT NULL,
    period_key varchar(10) NOT NULL,
    used_count int NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, activity_id, period_type, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS raffle_order (
    order_id varchar(64) NOT NULL,
    user_id varchar(32) NOT NULL,
    activity_id bigint NOT NULL,
    strategy_id bigint NOT NULL,
    request_no varchar(64) NOT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-processing 1-completed 2-cancelled',
    award_id int DEFAULT NULL,
    award_type tinyint DEFAULT NULL,
    draw_day date NOT NULL,
    draw_month char(7) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_user_request (user_id, request_no),
    KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user_award_record
    ADD COLUMN raffle_order_id varchar(64) DEFAULT NULL,
    ADD UNIQUE KEY uk_user_raffle_order (user_id, raffle_order_id);

CREATE TABLE IF NOT EXISTS award_delivery_task (
    order_id varchar(64) NOT NULL,
    user_id varchar(32) NOT NULL,
    award_id int NOT NULL,
    award_type tinyint NOT NULL,
    award_key varchar(64) NOT NULL,
    award_value varchar(256) DEFAULT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-pending 1-processing 2-success 3-manual 4-failed',
    attempts int NOT NULL DEFAULT 0,
    next_retry_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error varchar(500) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    KEY idx_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_points_account (
    user_id varchar(32) NOT NULL,
    points bigint NOT NULL DEFAULT 0,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_points_ledger (
    order_id varchar(64) NOT NULL,
    user_id varchar(32) NOT NULL,
    points int NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sku_rebate_config (
    sku_id bigint NOT NULL,
    rebate_draw_count int NOT NULL,
    status tinyint NOT NULL DEFAULT 1,
    PRIMARY KEY (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sku_rebate_order (
    purchase_order_id varchar(64) NOT NULL,
    user_id varchar(32) NOT NULL,
    activity_id bigint NOT NULL,
    rebate_draw_count int NOT NULL,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0-pending 1-completed',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (purchase_order_id),
    KEY idx_pending (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
