DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS cache_change_event;
DROP TABLE IF EXISTS user_notification;
DROP TABLE IF EXISTS notify_task;
DROP TABLE IF EXISTS group_member;
DROP TABLE IF EXISTS group_team;
DROP TABLE IF EXISTS group_activity;
DROP TABLE IF EXISTS shop_review;
DROP TABLE IF EXISTS deal_order;
DROP TABLE IF EXISTS voucher;
DROP TABLE IF EXISTS shop;
DROP TABLE IF EXISTS user_account;

CREATE TABLE user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE shop (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    address VARCHAR(255) NOT NULL,
    avg_score DECIMAL(4,2) NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    hot_score INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE voucher (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    stock INT NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    begin_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_voucher_shop (shop_id)
);

CREATE TABLE group_activity (
    id BIGINT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    required_size INT NOT NULL,
    group_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    joined_count INT NOT NULL DEFAULT 0,
    allowed_role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL,
    begin_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE group_team (
    id BIGINT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    leader_user_id BIGINT NOT NULL,
    current_size INT NOT NULL DEFAULT 0,
    required_size INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expire_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_group_activity_status (activity_id, status)
);

CREATE TABLE group_member (
    id BIGINT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_group_user (group_id, user_id),
    UNIQUE KEY uk_activity_user (activity_id, user_id)
);

CREATE TABLE deal_order (
    id BIGINT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NULL,
    canceled_at TIMESTAMP NULL,
    refunded_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_voucher_user (voucher_id, user_id),
    INDEX idx_order_user_status (user_id, status),
    INDEX idx_order_status_time (status, created_at)
);

CREATE TABLE shop_review (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    content VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_review_shop_user (shop_id, user_id),
    INDEX idx_review_shop_time (shop_id, created_at)
);

CREATE TABLE outbox_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time TIMESTAMP NOT NULL,
    error_message VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_outbox_status_retry (status, next_retry_time)
);

CREATE TABLE user_notification (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    type VARCHAR(32) NOT NULL,
    read_status BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_notification_user_time (user_id, created_at)
);

CREATE TABLE notify_task (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_notify_task_status_retry (status, next_retry_time)
);

CREATE TABLE cache_change_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMP NULL,
    INDEX idx_cache_change_status (status, id)
);
