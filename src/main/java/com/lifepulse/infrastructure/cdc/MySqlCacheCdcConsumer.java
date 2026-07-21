package com.lifepulse.infrastructure.cdc;

import com.lifepulse.shop.ShopService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Consumes rows emitted by MySQL triggers, keeping local cache invalidation decoupled from write services. */
@Service
@ConditionalOnProperty(prefix = "lifepulse.cdc", name = "enabled", havingValue = "true")
public class MySqlCacheCdcConsumer {
    private final JdbcTemplate jdbc;
    private final ShopService shops;
    public MySqlCacheCdcConsumer(JdbcTemplate jdbc, ShopService shops) { this.jdbc = jdbc; this.shops = shops; }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        List<Map<String, Object>> events = jdbc.queryForList("select id, aggregate_id from cache_change_event where status = 'PENDING' order by id limit 100");
        for (Map<String, Object> event : events) {
            long id = ((Number) event.get("id")).longValue();
            long shopId = ((Number) event.get("aggregate_id")).longValue();
            shops.evict(shopId);
            jdbc.update("update cache_change_event set status = 'DONE', consumed_at = now() where id = ? and status = 'PENDING'", id);
        }
    }
}
