package com.lifepulse.infrastructure.bloom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifepulse.entity.Shop;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.ShopMapper;
import com.lifepulse.mapper.VoucherMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BloomFilterWarmup {
    private final BloomFilterService bloomFilterService;
    private final ShopMapper shopMapper;
    private final VoucherMapper voucherMapper;

    public BloomFilterWarmup(BloomFilterService bloomFilterService,
                             ShopMapper shopMapper,
                             VoucherMapper voucherMapper) {
        this.bloomFilterService = bloomFilterService;
        this.shopMapper = shopMapper;
        this.voucherMapper = voucherMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        shopMapper.selectList(new LambdaQueryWrapper<Shop>().select(Shop::getId))
                .forEach(shop -> bloomFilterService.addShop(shop.getId()));
        voucherMapper.selectList(new LambdaQueryWrapper<Voucher>().select(Voucher::getId))
                .forEach(voucher -> bloomFilterService.addVoucher(voucher.getId()));
    }
}
