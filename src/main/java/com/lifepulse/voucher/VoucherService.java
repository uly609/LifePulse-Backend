package com.lifepulse.voucher;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.config.RocketTopics;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.order.OrderProcessor;
import com.lifepulse.outbox.OutboxEventService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class VoucherService {
    private static final String STOCK_KEY = "lifepulse:voucher:stock:";
    private static final String USERS_KEY = "lifepulse:voucher:users:";
    private static final String TOKEN_KEY = "lifepulse:voucher:qualification:";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[3]) ~= ARGV[1] then
                return 3
            end
            if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then
                return 1
            end
            if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                return 2
            end
            redis.call('decr', KEYS[1])
            redis.call('sadd', KEYS[2], ARGV[1])
            redis.call('del', KEYS[3])
            return 0
            """, Long.class);

    private final VoucherMapper voucherMapper;
    private final OutboxEventService outboxEventService;
    private final OrderProcessor orderProcessor;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LifePulseProperties properties;
    private final RedissonClient redissonClient;
    private final Map<String, Long> localQualificationTokens = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> localRegisteredUsers = new ConcurrentHashMap<>();
    private final ReentrantLock[] localLocks;

    public VoucherService(VoucherMapper voucherMapper,
                          OutboxEventService outboxEventService,
                          OrderProcessor orderProcessor,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          LifePulseProperties properties,
                          ObjectProvider<RedissonClient> redissonClientProvider) {
        this.voucherMapper = voucherMapper;
        this.outboxEventService = outboxEventService;
        this.orderProcessor = orderProcessor;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redissonClient = redissonClientProvider.getIfAvailable();
        this.localLocks = new ReentrantLock[Math.max(1, properties.getSeckill().getLocalLockStripes())];
        for (int i = 0; i < localLocks.length; i++) {
            localLocks[i] = new ReentrantLock();
        }
    }

    public List<Voucher> listSelling() {
        return voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getStatus, "SELLING")
                .orderByAsc(Voucher::getSalePrice));
    }

    public QualificationResponse applyQualification(Long voucherId, Long userId) {
        Voucher voucher = requireSellingVoucher(voucherId);
        String token = UUID.randomUUID().toString().replace("-", "");
        if (properties.getRedis().isEnabled()) {
            RLock lock = redissonClient == null ? null : redissonClient.getLock("lock:lifepulse:qualification:" + voucherId + ":" + userId);
            boolean locked = false;
            try {
                if (lock != null) {
                    locked = lock.tryLock(1, 3, TimeUnit.SECONDS);
                    if (!locked) {
                        throw new BusinessException("资格申请太频繁，请稍后再试");
                    }
                }
                redisTemplate.opsForValue().set(tokenKey(voucherId, token),
                        String.valueOf(userId),
                        properties.getSeckill().getTokenTtlSeconds(),
                        TimeUnit.SECONDS);
                redisTemplate.opsForValue().setIfAbsent(stockKey(voucherId),
                        String.valueOf(voucher.getStock()),
                        redisTtl(voucher));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("资格申请被中断");
            } finally {
                if (locked && lock != null && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } else {
            localQualificationTokens.put(tokenKey(voucherId, token), userId);
        }
        return new QualificationResponse(token, properties.getSeckill().getTokenTtlSeconds());
    }

    public EnrollResponse seckill(Long voucherId, Long userId, String qualificationToken) {
        Voucher voucher = requireSellingVoucher(voucherId);
        if (qualificationToken == null || qualificationToken.isBlank()) {
            throw new BusinessException("请先申请抢券资格");
        }
        if (properties.getRedis().isEnabled()) {
            checkByRedis(voucherId, userId, qualificationToken);
        } else {
            checkByLocalLock(voucherId, userId, qualificationToken, voucher);
        }
        OrderCreateMessage message = new OrderCreateMessage(
                voucher.getId(), voucher.getShopId(), userId, voucher.getSalePrice());
        sendOrderCreateMessage(message);
        return new EnrollResponse("PROCESSING", "抢券资格已占用，订单创建中");
    }

    private Voucher requireSellingVoucher(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher == null || !"SELLING".equals(voucher.getStatus())) {
            throw new BusinessException("优惠券不存在或未开售");
        }
        if (now.isBefore(voucher.getBeginTime()) || now.isAfter(voucher.getEndTime())) {
            throw new BusinessException("不在抢券时间内");
        }
        return voucher;
    }

    private void checkByRedis(Long voucherId, Long userId, String token) {
        Long code = redisTemplate.execute(SECKILL_SCRIPT,
                Arrays.asList(stockKey(voucherId), usersKey(voucherId), tokenKey(voucherId, token)),
                String.valueOf(userId));
        if (code == null || code == 3) {
            throw new BusinessException("抢券资格无效或已过期");
        }
        if (code == 1) {
            throw new BusinessException("优惠券已抢完");
        }
        if (code == 2) {
            throw new BusinessException("不能重复抢同一张券");
        }
    }

    private void checkByLocalLock(Long voucherId, Long userId, String token, Voucher voucher) {
        ReentrantLock lock = localLocks[Math.floorMod(voucherId.hashCode(), localLocks.length)];
        lock.lock();
        try {
            Long tokenUserId = localQualificationTokens.remove(tokenKey(voucherId, token));
            if (!userId.equals(tokenUserId)) {
                throw new BusinessException("抢券资格无效或已过期");
            }
            Set<Long> users = localRegisteredUsers.computeIfAbsent(voucherId, ignored -> ConcurrentHashMap.newKeySet());
            if (users.contains(userId)) {
                throw new BusinessException("不能重复抢同一张券");
            }
            if (voucher.getStock() <= users.size()) {
                throw new BusinessException("优惠券已抢完");
            }
            users.add(userId);
        } finally {
            lock.unlock();
        }
    }

    private void sendOrderCreateMessage(OrderCreateMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            outboxEventService.saveAndSend("ORDER_CREATE",
                    message.voucherId(),
                    RocketTopics.ORDER_CREATE,
                    payload,
                    () -> orderProcessor.createPendingOrder(message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单消息序列化失败", e);
        }
    }

    public void restoreDbStock(Long voucherId) {
        voucherMapper.update(null, new LambdaUpdateWrapper<Voucher>()
                .eq(Voucher::getId, voucherId)
                .setSql("stock = stock + 1")
                .set(Voucher::getUpdatedAt, LocalDateTime.now()));
    }

    public void rollbackRedisQualification(Long voucherId, Long userId) {
        if (properties.getRedis().isEnabled()) {
            Long removed = redisTemplate.opsForSet().remove(usersKey(voucherId), String.valueOf(userId));
            if (removed != null && removed > 0) {
                redisTemplate.opsForValue().increment(stockKey(voucherId));
            }
        } else {
            Set<Long> users = localRegisteredUsers.get(voucherId);
            if (users != null) {
                users.remove(userId);
            }
        }
    }

    private Duration redisTtl(Voucher voucher) {
        Duration ttl = Duration.between(LocalDateTime.now(), voucher.getEndTime().plusMinutes(10));
        return ttl.isPositive() ? ttl : Duration.ofMinutes(10);
    }

    private String stockKey(Long voucherId) {
        return STOCK_KEY + voucherId;
    }

    private String usersKey(Long voucherId) {
        return USERS_KEY + voucherId;
    }

    private String tokenKey(Long voucherId, String token) {
        return TOKEN_KEY + voucherId + ":" + token;
    }
}
