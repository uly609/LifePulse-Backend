package com.lifepulse.config.policy;

import com.lifepulse.config.LifePulseProperties;
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
    private volatile String lastContent = "";

    public NacosPolicyService(LifePulseProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 5000)
    public void refresh() {
        try {
            String content = client.get().uri(configUrl()).retrieve().body(String.class);
            if (content != null && !content.equals(lastContent)) {
                RuntimePolicy.replace(parse(content));
                lastContent = content;
            }
        } catch (Exception ignored) {
            // Nacos may not have the policy data yet; retain the last valid runtime snapshot.
        }
    }

    public RuntimePolicy.Snapshot current() { return RuntimePolicy.current(); }

    public void publish(RuntimePolicy.Snapshot policy) {
        String content = "shop-cache-ttl-seconds: " + policy.shopCacheTtlSeconds() + "\nseckill-enabled: " + policy.seckillEnabled();
        client.post().uri(properties.getNacos().getServerAddr() + "/nacos/v1/cs/configs")
                .body("dataId=" + encode(properties.getNacos().getDataId()) + "&group=" + encode(properties.getNacos().getGroup()) + "&content=" + encode(content))
                .header("Content-Type", "application/x-www-form-urlencoded").retrieve().toBodilessEntity();
        RuntimePolicy.replace(policy);
        lastContent = content;
    }

    private RuntimePolicy.Snapshot parse(String source) {
        long ttl = value(source, "shop-cache-ttl-seconds", 600L);
        boolean enabled = Boolean.parseBoolean(stringValue(source, "seckill-enabled", "true"));
        return new RuntimePolicy.Snapshot(Math.max(30, ttl), enabled);
    }
    private long value(String source, String key, long fallback) { try { return Long.parseLong(stringValue(source, key, String.valueOf(fallback))); } catch (Exception ignored) { return fallback; } }
    private String stringValue(String source, String key, String fallback) { for (String line : source.split("\\R")) { String[] pair = line.split(":", 2); if (pair.length == 2 && key.equals(pair[0].trim())) return pair[1].trim(); } return fallback; }
    private String configUrl() { return properties.getNacos().getServerAddr() + "/nacos/v1/cs/configs?dataId=" + encode(properties.getNacos().getDataId()) + "&group=" + encode(properties.getNacos().getGroup()); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
