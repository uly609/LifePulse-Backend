package com.lifepulse.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.dcc.DccValue;
import com.lifepulse.infrastructure.bloom.BloomFilterService;
import com.lifepulse.infrastructure.cache.LocalCacheTemplate;
import com.lifepulse.entity.Shop;
import com.lifepulse.mapper.ShopMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShopService {
    private final ShopMapper shopMapper;
    private final LocalCacheTemplate cacheTemplate;
    private final BloomFilterService bloomFilterService;
    private final Cache<Long, LocalCacheTemplate.CacheEntry<Shop>> shopCache = Caffeine.newBuilder().maximumSize(1000).build();
    @DccValue("shop-cache-ttl-seconds")
    private volatile long shopCacheTtlSeconds = 600;

    public ShopService(ShopMapper shopMapper, LocalCacheTemplate cacheTemplate, BloomFilterService bloomFilterService) {
        this.shopMapper = shopMapper;
        this.cacheTemplate = cacheTemplate;
        this.bloomFilterService = bloomFilterService;
    }

    public List<Shop> listHot() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, "OPEN")
                .orderByDesc(Shop::getHotScore));
    }

    public Shop detail(Long id) {
        if (!bloomFilterService.mightContainShop(id)) {
            throw new BusinessException("商户不存在");
        }
        Duration ttl = Duration.ofSeconds(shopCacheTtlSeconds);
        return cacheTemplate.getFromCacheOrDb(shopCache, id, ttl, () -> shopMapper.selectById(id));
    }

    public void evict(Long shopId) {
        cacheTemplate.evict(shopCache, shopId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Shop updateByAdmin(Long shopId, ShopAdminUpdateRequest request) {
        if (!bloomFilterService.mightContainShop(shopId)) {
            throw new BusinessException("商户不存在");
        }
        LambdaUpdateWrapper<Shop> wrapper = new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .set(Shop::getUpdatedAt, LocalDateTime.now());
        if (request.status() != null) {
            wrapper.set(Shop::getStatus, request.status());
        }
        if (request.hotScore() != null) {
            wrapper.set(Shop::getHotScore, request.hotScore());
        }
        int updated = shopMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException("商户不存在");
        }
        evict(shopId);
        return shopMapper.selectById(shopId);
    }
}
