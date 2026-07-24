package com.lifepulse.infrastructure.limit;

import com.lifepulse.config.LifePulseProperties;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrafficLimiterService {
    private static final String REDIS_PREFIX = "lifepulse:limit:";

    private final LifePulseProperties properties;
    private final RedissonClient redissonClient;
    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public TrafficLimiterService(LifePulseProperties properties,
                                 ObjectProvider<RedissonClient> redissonClientProvider) {
        this.properties = properties;
        this.redissonClient = redissonClientProvider.getIfAvailable();
    }

    public boolean tryAcquire(String key, int permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            return true;
        }
        if (properties.getRedis().isEnabled() && redissonClient != null) {
            RRateLimiter limiter = redissonClient.getRateLimiter(REDIS_PREFIX + key);
            limiter.trySetRate(RateType.OVERALL, permitsPerSecond, 1, RateIntervalUnit.SECONDS);
            return limiter.tryAcquire();
        }
        return localBuckets
                .computeIfAbsent(key, ignored -> new LocalBucket(permitsPerSecond, clock.millis()))
                .tryAcquire(permitsPerSecond, clock.millis());
    }

    private static final class LocalBucket {
        private int tokens;
        private long lastRefillMillis;

        private LocalBucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillMillis = now;
        }

        private synchronized boolean tryAcquire(int capacity, long now) {
            long elapsedMillis = Math.max(0, now - lastRefillMillis);
            int refill = (int) (elapsedMillis * capacity / 1000);
            if (refill > 0) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefillMillis = now;
            }
            if (tokens <= 0) {
                return false;
            }
            tokens--;
            return true;
        }
    }
}
