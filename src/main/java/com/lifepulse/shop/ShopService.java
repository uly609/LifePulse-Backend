package com.lifepulse.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lifepulse.config.policy.RuntimePolicy;
import com.lifepulse.entity.Shop;
import com.lifepulse.mapper.ShopMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ShopService {
    private final ShopMapper shopMapper;
    private final Cache<Long, CachedShop> shopCache = Caffeine.newBuilder().maximumSize(1000).build();

    public ShopService(ShopMapper shopMapper) {
        this.shopMapper = shopMapper;
    }

    public List<Shop> listHot() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, "OPEN")
                .orderByDesc(Shop::getHotScore));
    }

    public Shop detail(Long id) {
        CachedShop cached = shopCache.getIfPresent(id);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.shop();
        }
        Shop shop = shopMapper.selectById(id);
        if (shop != null) {
            shopCache.put(id, new CachedShop(shop, Instant.now().plusSeconds(RuntimePolicy.current().shopCacheTtlSeconds())));
        }
        return shop;
    }

    public void evict(Long shopId) {
        shopCache.invalidate(shopId);
    }

    private record CachedShop(Shop shop, Instant expiresAt) { }
}
