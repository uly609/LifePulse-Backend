package com.lifepulse.voucher;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.VoucherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优惠券 ID 布隆过滤器。
 *
 * <p>用于在访问数据库前拦截一定不存在的优惠券 ID，降低恶意或无效 ID
 * 造成的缓存穿透。布隆过滤器可能产生误判，因此判断“可能存在”时仍需继续
 * 查询数据库；初始化完成前采用放行策略，避免启动阶段出现假阴性。</p>
 */
@Component
public class VoucherBloomFilter {
    private static final Logger log = LoggerFactory.getLogger(VoucherBloomFilter.class);
    private static final long EXPECTED_INSERTIONS = 100_000L;
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01D;

    private final VoucherMapper voucherMapper;

    private volatile BloomFilter<Long> filter = newFilter();
    private volatile boolean initialized;

    public VoucherBloomFilter(VoucherMapper voucherMapper) {
        this.voucherMapper = voucherMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void rebuild() {
        BloomFilter<Long> rebuilt = newFilter();
        List<Voucher> vouchers = voucherMapper.selectList(null);
        for (Voucher voucher : vouchers) {
            if (voucher.getId() != null) {
                rebuilt.put(voucher.getId());
            }
        }
        filter = rebuilt;
        initialized = true;
        log.info("voucher_bloom_filter_rebuilt size={}", vouchers.size());
    }

    /**
     * 返回 false 表示该 ID 一定不存在；返回 true 表示可能存在，需要继续查库。
     */
    public boolean mightContain(Long voucherId) {
        if (voucherId == null) {
            return false;
        }
        return !initialized || filter.mightContain(voucherId);
    }

    /**
     * 新增优惠券成功落库后调用，避免新增数据被过滤器误拦截。
     */
    public void put(Long voucherId) {
        if (voucherId != null) {
            filter.put(voucherId);
        }
    }

    private static BloomFilter<Long> newFilter() {
        return BloomFilter.create(
                Funnels.longFunnel(),
                EXPECTED_INSERTIONS,
                FALSE_POSITIVE_PROBABILITY
        );
    }
}
