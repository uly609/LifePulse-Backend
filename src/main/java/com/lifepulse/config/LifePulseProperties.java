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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
}
