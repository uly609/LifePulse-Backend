package com.lifepulse.group;

import com.lifepulse.common.BusinessException;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.GroupTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class GroupInventoryService {
    private static final Logger log = LoggerFactory.getLogger(GroupInventoryService.class);
    private static final String KEY_PREFIX = "lifepulse:group:";

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('setnx', KEYS[1], ARGV[2])
            if ARGV[4] == '1' then
                redis.call('setnx', KEYS[2], ARGV[3])
            end
            if redis.call('sismember', KEYS[3], ARGV[1]) == 1 then
                return 2
            end
            if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then
                return 1
            end
            if ARGV[4] == '1' and tonumber(redis.call('get', KEYS[2]) or '0') <= 0 then
                return 3
            end
            redis.call('decr', KEYS[1])
            if ARGV[4] == '1' then
                redis.call('decr', KEYS[2])
            end
            redis.call('sadd', KEYS[3], ARGV[1])
            redis.call('expire', KEYS[1], ARGV[5])
            redis.call('expire', KEYS[3], ARGV[5])
            if ARGV[4] == '1' then
                redis.call('expire', KEYS[2], ARGV[5])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('srem', KEYS[2], ARGV[1]) == 0 then
                return 0
            end
            redis.call('incr', KEYS[1])
            if ARGV[2] == '1' then
                redis.call('incr', KEYS[3])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LifePulseProperties properties;

    public GroupInventoryService(StringRedisTemplate redisTemplate, LifePulseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Reservation reserveForCreate(GroupActivity activity, Long userId) {
        return reserve(activity, null, userId);
    }

    public Reservation reserveForJoin(GroupActivity activity, GroupTeam team, Long userId) {
        return reserve(activity, team, userId);
    }

    public void initializeCreatedTeamAfterCommit(GroupActivity activity, GroupTeam team) {
        if (!properties.getRedis().isEnabled()) {
            return;
        }
        afterCommit(() -> {
            try {
                redisTemplate.opsForValue().setIfAbsent(
                        teamStockKey(activity.getId(), team.getId()),
                        String.valueOf(Math.max(0, team.getRequiredSize() - team.getCurrentSize())),
                        redisTtl(activity));
            } catch (RuntimeException error) {
                log.error("group_team_inventory_init_failed activityId={} groupId={}",
                        activity.getId(), team.getId(), error);
            }
        });
    }

    public void releaseParticipationAfterCommit(Long activityId, Long groupId, Long userId) {
        if (!properties.getRedis().isEnabled()) {
            return;
        }
        afterCommit(() -> release(new Reservation(true, activityId, groupId, userId, false)));
    }

    public void deleteTeamInventoryAfterCommit(Long activityId, Long groupId) {
        if (!properties.getRedis().isEnabled()) {
            return;
        }
        afterCommit(() -> {
            try {
                redisTemplate.delete(teamStockKey(activityId, groupId));
            } catch (RuntimeException error) {
                log.error("group_team_inventory_delete_failed activityId={} groupId={}",
                        activityId, groupId, error);
            }
        });
    }

    private Reservation reserve(GroupActivity activity, GroupTeam team, Long userId) {
        if (!properties.getRedis().isEnabled()) {
            return Reservation.local(activity.getId(), team == null ? null : team.getId(), userId);
        }
        boolean reserveTeam = team != null;
        long activityRemaining = Math.max(0L, activity.getTotalStock() - activity.getJoinedCount());
        long teamRemaining = reserveTeam
                ? Math.max(0L, team.getRequiredSize() - team.getCurrentSize())
                : 0L;
        String teamKey = reserveTeam
                ? teamStockKey(activity.getId(), team.getId())
                : unusedTeamKey(activity.getId());
        Long code;
        try {
            code = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    Arrays.asList(activityStockKey(activity.getId()), teamKey, usersKey(activity.getId())),
                    String.valueOf(userId),
                    String.valueOf(activityRemaining),
                    String.valueOf(teamRemaining),
                    reserveTeam ? "1" : "0",
                    String.valueOf(redisTtl(activity).toSeconds()));
        } catch (RuntimeException error) {
            log.warn("group_inventory_redis_unavailable activityId={} groupId={} fallback=mysql",
                    activity.getId(), team == null ? null : team.getId(), error);
            return Reservation.local(activity.getId(), team == null ? null : team.getId(), userId);
        }
        if (code == null) {
            throw new BusinessException("拼团库存校验失败，请稍后重试");
        }
        if (code == 1L) {
            throw new BusinessException("活动名额已满");
        }
        if (code == 2L) {
            throw new BusinessException("你已参加该活动");
        }
        if (code == 3L) {
            throw new BusinessException("该拼团人数已满");
        }
        Reservation reservation = new Reservation(true, activity.getId(),
                team == null ? null : team.getId(), userId, reserveTeam);
        compensateOnRollback(reservation);
        return reservation;
    }

    private void compensateOnRollback(Reservation reservation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(reservation);
            throw new IllegalStateException("拼团Redis预占必须在数据库事务中执行");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    release(reservation);
                }
            }
        });
    }

    private void release(Reservation reservation) {
        if (!reservation.redisReserved()) {
            return;
        }
        try {
            Long released = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(
                            activityStockKey(reservation.activityId()),
                            usersKey(reservation.activityId()),
                            reservation.groupId() == null
                                    ? unusedTeamKey(reservation.activityId())
                                    : teamStockKey(reservation.activityId(), reservation.groupId())),
                    String.valueOf(reservation.userId()),
                    reservation.teamReserved() ? "1" : "0");
            log.info("group_inventory_released activityId={} groupId={} userId={} released={}",
                    reservation.activityId(), reservation.groupId(), reservation.userId(), released);
        } catch (RuntimeException error) {
            log.error("group_inventory_compensation_failed activityId={} groupId={} userId={}",
                    reservation.activityId(), reservation.groupId(), reservation.userId(), error);
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

    private Duration redisTtl(GroupActivity activity) {
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime().plusHours(1));
        return ttl.isPositive() ? ttl : Duration.ofHours(1);
    }

    private String activityStockKey(Long activityId) {
        return KEY_PREFIX + "{" + activityId + "}:stock";
    }

    private String teamStockKey(Long activityId, Long groupId) {
        return KEY_PREFIX + "{" + activityId + "}:team:" + groupId + ":stock";
    }

    private String unusedTeamKey(Long activityId) {
        return KEY_PREFIX + "{" + activityId + "}:team:none";
    }

    private String usersKey(Long activityId) {
        return KEY_PREFIX + "{" + activityId + "}:users";
    }

    public record Reservation(boolean redisReserved, Long activityId, Long groupId,
                              Long userId, boolean teamReserved) {
        static Reservation local(Long activityId, Long groupId, Long userId) {
            return new Reservation(false, activityId, groupId, userId, groupId != null);
        }
    }
}
