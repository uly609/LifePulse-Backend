INSERT INTO user_account (id, username, password_hash, role, created_at) VALUES
(10001, 'student', '{noop}123456', 'USER', CURRENT_TIMESTAMP),
(10002, 'merchant', '{noop}123456', 'MERCHANT', CURRENT_TIMESTAMP),
(10003, 'admin', '{noop}123456', 'ADMIN', CURRENT_TIMESTAMP);

INSERT INTO shop (id, name, category, address, avg_score, comment_count, hot_score, status, created_at, updated_at) VALUES
(1, '云上咖啡馆', '咖啡甜品', '西溪校区 3 号楼旁', 4.70, 82, 980, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '青禾轻食', '轻食简餐', '金沙港生活区 B1', 4.50, 56, 710, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, '星火桌游社', '休闲娱乐', '创业街 2 楼', 4.80, 121, 1240, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO voucher (id, shop_id, title, stock, original_price, sale_price, status, begin_time, end_time, created_at, updated_at) VALUES
(1, 1, '咖啡双人下午茶秒杀券', 20, 88.00, 39.90, 'SELLING', CURRENT_TIMESTAMP, DATEADD('DAY', 7, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, '低脂轻食套餐团购券', 30, 42.00, 19.90, 'SELLING', CURRENT_TIMESTAMP, DATEADD('DAY', 10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, '桌游包间 2 小时体验券', 12, 98.00, 49.90, 'SELLING', CURRENT_TIMESTAMP, DATEADD('DAY', 5, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
