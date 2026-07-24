package com.lifepulse.config.policy;

import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.config.dcc.DccValueRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(prefix = "lifepulse.nacos", name = "enabled", havingValue = "true")
public class NacosPolicyService {
    private final LifePulseProperties properties;
    private final RestClient client;
    private final DccValueRegistry dccValueRegistry;
    private volatile String lastContent = "";

    public NacosPolicyService(LifePulseProperties properties, RestClient.Builder builder, DccValueRegistry dccValueRegistry) {
        this.properties = properties;
        this.client = builder.build();
        this.dccValueRegistry = dccValueRegistry;
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 5000)
    public void refresh() {
        try {
            String content = client.get().uri(configUrl()).retrieve().body(String.class);
            if (content != null && !content.equals(lastContent)) {
                RuntimePolicy.replace(parse(content));
                dccValueRegistry.refreshAll();
                lastContent = content;
            }
        } catch (Exception ignored) {
            // Nacos may not have the policy data yet; retain the last valid runtime snapshot.
        }
    }

    public RuntimePolicy.Snapshot current() { return RuntimePolicy.current(); }

    public void publish(RuntimePolicy.Snapshot policy) {
        String content = "shop-cache-ttl-seconds: " + policy.shopCacheTtlSeconds()
                + "\nseckill-enabled: " + policy.seckillEnabled()
                + "\nseckill-rate-limit-per-second: " + policy.seckillRateLimitPerSecond()
                + "\ngroup-rate-limit-per-second: " + policy.groupRateLimitPerSecond()
                + "\norder-timeout-minutes: " + policy.orderTimeoutMinutes();
        client.post().uri(properties.getNacos().getServerAddr() + "/nacos/v1/cs/configs")
                .body("dataId=" + encode(properties.getNacos().getDataId()) + "&group=" + encode(properties.getNacos().getGroup()) + "&content=" + encode(content))
                .header("Content-Type", "application/x-www-form-urlencoded").retrieve().toBodilessEntity();
        RuntimePolicy.replace(policy);
        dccValueRegistry.refreshAll();
        lastContent = content;
    }

    private RuntimePolicy.Snapshot parse(String source) {
        long ttl = value(source, "shop-cache-ttl-seconds", 600L);
        boolean enabled = Boolean.parseBoolean(stringValue(source, "seckill-enabled", "true"));
        long seckillRate = value(source, "seckill-rate-limit-per-second", 80L);
        long groupRate = value(source, "group-rate-limit-per-second", 50L);
        long orderTimeout = value(source, "order-timeout-minutes", 15L);
        return new RuntimePolicy.Snapshot(Math.max(30, ttl), enabled,
                (int) Math.max(1, seckillRate), (int) Math.max(1, groupRate),
                (int) Math.max(1, orderTimeout));
    }
    private long value(String source, String key, long fallback) { try { return Long.parseLong(stringValue(source, key, String.valueOf(fallback))); } catch (Exception ignored) { return fallback; } }
    private String stringValue(String source, String key, String fallback) { for (String line : source.split("\\R")) { String[] pair = line.split(":", 2); if (pair.length == 2 && key.equals(pair[0].trim())) return pair[1].trim(); } return fallback; }
    private String configUrl() { return properties.getNacos().getServerAddr() + "/nacos/v1/cs/configs?dataId=" + encode(properties.getNacos().getDataId()) + "&group=" + encode(properties.getNacos().getGroup()); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
