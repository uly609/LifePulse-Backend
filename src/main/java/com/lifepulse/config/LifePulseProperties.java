package com.lifepulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lifepulse")
public class LifePulseProperties {
    private final Jwt jwt = new Jwt();
    private final Redis redis = new Redis();
    private final Mq mq = new Mq();
    private final Seckill seckill = new Seckill();
    private final Ai ai = new Ai();
    private final Nacos nacos = new Nacos();
    private final Cdc cdc = new Cdc();
    private final Observability observability = new Observability();

    public Jwt getJwt() {
        return jwt;
    }

    public Redis getRedis() {
        return redis;
    }

    public Mq getMq() {
        return mq;
    }

    public Seckill getSeckill() {
        return seckill;
    }

    public Ai getAi() {
        return ai;
    }

    public Nacos getNacos() { return nacos; }
    public Cdc getCdc() { return cdc; }
    public Observability getObservability() { return observability; }
    public static class Cdc {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Observability {
        private String elasticsearchUrl = "http://localhost:9200";
        private String prometheusUrl = "http://localhost:9090";
        private String logIndexPattern = "lifepulse-*";

        public String getElasticsearchUrl() { return elasticsearchUrl; }
        public void setElasticsearchUrl(String elasticsearchUrl) { this.elasticsearchUrl = elasticsearchUrl; }
        public String getPrometheusUrl() { return prometheusUrl; }
        public void setPrometheusUrl(String prometheusUrl) { this.prometheusUrl = prometheusUrl; }
        public String getLogIndexPattern() { return logIndexPattern; }
        public void setLogIndexPattern(String logIndexPattern) { this.logIndexPattern = logIndexPattern; }
    }

    public static class Nacos {
        private boolean enabled;
        private String serverAddr = "http://localhost:8848";
        private String dataId = "lifepulse-policy.yaml";
        private String group = "DEFAULT_GROUP";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServerAddr() { return serverAddr; }
        public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
        public String getDataId() { return dataId; }
        public void setDataId(String dataId) { this.dataId = dataId; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
    }

    public static class Jwt {
        private String secret;
        private long ttlSeconds = 7200;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Redis {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Mq {
        private boolean enabled;
        private int maxRetryCount = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public void setMaxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }
    }

    public static class Seckill {
        private long tokenTtlSeconds = 60;
        private int localLockStripes = 64;

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        public int getLocalLockStripes() {
            return localLockStripes;
        }

        public void setLocalLockStripes(int localLockStripes) {
            this.localLockStripes = localLockStripes;
        }
    }

    public static class Ai {
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com/chat/completions";
        private String model = "deepseek-chat";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
