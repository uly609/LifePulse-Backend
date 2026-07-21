package com.lifepulse.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lifepulse.infrastructure.cache.LocalCacheTemplate;
import com.lifepulse.config.policy.RuntimePolicy;
import com.lifepulse.entity.Shop;
import com.lifepulse.mapper.ShopMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ShopService {
    private final ShopMapper shopMapper;
    private final LocalCacheTemplate cacheTemplate;
    private final Cache<Long, LocalCacheTemplate.CacheEntry<Shop>> shopCache = Caffeine.newBuilder().maximumSize(1000).build();

    public ShopService(ShopMapper shopMapper, LocalCacheTemplate cacheTemplate) {
        this.shopMapper = shopMapper;
        this.cacheTemplate = cacheTemplate;
    }

    public List<Shop> listHot() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, "OPEN")
                .orderByDesc(Shop::getHotScore));
    }

    public Shop detail(Long id) {
        Duration ttl = Duration.ofSeconds(RuntimePolicy.current().shopCacheTtlSeconds());
        return cacheTemplate.getFromCacheOrDb(shopCache, id, ttl, () -> shopMapper.selectById(id));
    }

    public void evict(Long shopId) {
        cacheTemplate.evict(shopCache, shopId);
    }
}
