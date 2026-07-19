DROP TABLE IF EXISTS outbox_event;
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
