package com.lifepulse.group;

import com.lifepulse.config.LifePulseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

@Service
public class GroupParticipationBitmapService {
    private static final Logger log = LoggerFactory.getLogger(GroupParticipationBitmapService.class);
    private final StringRedisTemplate redisTemplate;
    private final LifePulseProperties properties;

    public GroupParticipationBitmapService(StringRedisTemplate redisTemplate, LifePulseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void markJoinedAfterCommit(Long activityId, Long userId) {
        if (!properties.getRedis().isEnabled()) {
            return;
        }
        afterCommit(() -> setJoined(activityId, userId, true));
    }

    public void clearJoinedAfterCommit(Long activityId, Long userId) {
        if (!properties.getRedis().isEnabled()) {
            return;
        }
        afterCommit(() -> setJoined(activityId, userId, false));
    }

    public boolean maybeJoined(Long activityId, Long userId) {
        if (!properties.getRedis().isEnabled()) {
            return false;
        }
        Boolean bit = redisTemplate.opsForValue().getBit(key(activityId), offset(userId));
        return Boolean.TRUE.equals(bit);
    }

    public long approximateJoinedCount(Long activityId) {
        if (!properties.getRedis().isEnabled()) {
            return 0L;
        }
        Long count = redisTemplate.execute((RedisConnection connection) ->
                connection.stringCommands().bitCount(key(activityId).getBytes(StandardCharsets.UTF_8)));
        return count == null ? 0L : count;
    }

    private void setJoined(Long activityId, Long userId, boolean joined) {
        try {
            redisTemplate.opsForValue().setBit(key(activityId), offset(userId), joined);
        } catch (RuntimeException error) {
            log.warn("group_participation_bitmap_update_failed activityId={} userId={} joined={}",
                    activityId, userId, joined, error);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String key(Long activityId) {
        return "lifepulse:group:bitmap:" + activityId;
    }

    private long offset(Long userId) {
        long mixed = userId ^ (userId >>> 32);
        return Math.floorMod(mixed, 1_000_000L);
    }
}
