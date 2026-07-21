package com.lifepulse.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

@Service
public class LocalCacheTemplate {

    public <K, V> V getFromCacheOrDb(Cache<K, CacheEntry<V>> cache, K key, Duration ttl, Supplier<V> dbLoader) {
        CacheEntry<V> cached = cache.getIfPresent(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        V value = dbLoader.get();
        if (value != null) {
            cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
        }
        return value;
    }

    public <K, V> void put(Cache<K, CacheEntry<V>> cache, K key, V value, Duration ttl) {
        if (value == null) {
            cache.invalidate(key);
            return;
        }
        cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
    }

    public <K, V> void evict(Cache<K, CacheEntry<V>> cache, K key) {
        cache.invalidate(key);
    }

    public record CacheEntry<V>(V value, Instant expiresAt) { }
}
