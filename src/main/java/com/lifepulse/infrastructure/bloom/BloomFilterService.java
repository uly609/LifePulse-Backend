package com.lifepulse.infrastructure.bloom;

import com.lifepulse.config.LifePulseProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BloomFilterService {
    private static final String SHOP_KEY = "lifepulse:bloom:shop";
    private static final String VOUCHER_KEY = "lifepulse:bloom:voucher";
    private static final int BIT_SIZE = 1 << 20;
    private static final int HASH_COUNT = 7;

    private final LifePulseProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, BitSet> localBits = new ConcurrentHashMap<>();

    public BloomFilterService(LifePulseProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public void addShop(Long id) {
        add(SHOP_KEY, id);
    }

    public void addVoucher(Long id) {
        add(VOUCHER_KEY, id);
    }

    public boolean mightContainShop(Long id) {
        return mightContain(SHOP_KEY, id);
    }

    public boolean mightContainVoucher(Long id) {
        return mightContain(VOUCHER_KEY, id);
    }

    private void add(String namespace, Long id) {
        if (id == null || id <= 0) {
            return;
        }
        if (properties.getRedis().isEnabled()) {
            for (int i = 0; i < HASH_COUNT; i++) {
                redisTemplate.opsForValue().setBit(namespace, index(namespace, id, i), true);
            }
            return;
        }
        BitSet bitSet = localBits.computeIfAbsent(namespace, ignored -> new BitSet(BIT_SIZE));
        synchronized (bitSet) {
            for (int i = 0; i < HASH_COUNT; i++) {
                bitSet.set(index(namespace, id, i));
            }
        }
    }

    private boolean mightContain(String namespace, Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        if (properties.getRedis().isEnabled()) {
            for (int i = 0; i < HASH_COUNT; i++) {
                Boolean bit = redisTemplate.opsForValue().getBit(namespace, index(namespace, id, i));
                if (bit == null || !bit) {
                    return false;
                }
            }
            return true;
        }
        BitSet bitSet = localBits.get(namespace);
        if (bitSet == null) {
            return false;
        }
        synchronized (bitSet) {
            for (int i = 0; i < HASH_COUNT; i++) {
                if (!bitSet.get(index(namespace, id, i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private int index(String namespace, Long id, int seed) {
        long hash = mix64(namespace.hashCode() * 31L + id * 1315423911L + seed * 2654435761L);
        return Math.floorMod(hash, BIT_SIZE);
    }

    private long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
