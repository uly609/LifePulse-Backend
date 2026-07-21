-- Apply once for an existing Docker MySQL volume created before CDC support.
CREATE TABLE IF NOT EXISTS cache_change_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at DATETIME NULL,
    INDEX idx_cache_change_status (status, id)
);

DROP TRIGGER IF EXISTS trg_shop_cache_change;
CREATE TRIGGER trg_shop_cache_change AFTER UPDATE ON shop
FOR EACH ROW INSERT INTO cache_change_event (aggregate_type, aggregate_id, operation_type)
VALUES ('SHOP', NEW.id, 'UPDATE');
