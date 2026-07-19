CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS shop (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    address VARCHAR(255) NOT NULL,
    avg_score DECIMAL(4,2) NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    hot_score INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS voucher (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    stock INT NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    begin_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_voucher_shop (shop_id)
);

CREATE TABLE IF NOT EXISTS deal_order (
    id BIGINT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    canceled_at DATETIME NULL,
    refunded_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_voucher_user (voucher_id, user_id),
    INDEX idx_order_user_status (user_id, status),
    INDEX idx_order_status_time (status, created_at)
);

CREATE TABLE IF NOT EXISTS shop_review (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    content VARCHAR(512) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_review_shop_user (shop_id, user_id),
    INDEX idx_review_shop_time (shop_id, created_at)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL,
    error_message VARCHAR(512),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_outbox_status_retry (status, next_retry_time)
);

INSERT IGNORE INTO user_account (id, username, password_hash, role, created_at) VALUES
(10001, 'student', '{noop}123456', 'USER', NOW()),
(10002, 'merchant', '{noop}123456', 'MERCHANT', NOW()),
(10003, 'admin', '{noop}123456', 'ADMIN', NOW());

INSERT IGNORE INTO shop (id, name, category, address, avg_score, comment_count, hot_score, status, created_at, updated_at) VALUES
(1, '云上咖啡馆', '咖啡甜品', '西溪校区 3 号楼旁', 4.70, 82, 980, 'OPEN', NOW(), NOW()),
(2, '青禾轻食', '轻食简餐', '金沙港生活区 B1', 4.50, 56, 710, 'OPEN', NOW(), NOW()),
(3, '星火桌游社', '休闲娱乐', '创业街 2 楼', 4.80, 121, 1240, 'OPEN', NOW(), NOW());

INSERT IGNORE INTO voucher (id, shop_id, title, stock, original_price, sale_price, status, begin_time, end_time, created_at, updated_at) VALUES
(1, 1, '咖啡双人下午茶秒杀券', 20, 88.00, 39.90, 'SELLING', NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(2, 2, '低脂轻食套餐团购券', 30, 42.00, 19.90, 'SELLING', NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), NOW(), NOW()),
(3, 3, '桌游包间 2 小时体验券', 12, 98.00, 49.90, 'SELLING', NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), NOW(), NOW());
