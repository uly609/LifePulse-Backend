package com.lifepulse.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.IdGenerator;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.entity.OutboxEvent;
import com.lifepulse.mapper.OutboxEventMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventService {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);
    private static final String PENDING = "PENDING";
    private static final String SENT = "SENT";
    private static final String FAILED = "FAILED";

    private final OutboxEventMapper outboxEventMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final LifePulseProperties properties;
    private final IdGenerator idGenerator;

    public OutboxEventService(OutboxEventMapper outboxEventMapper,
                              RocketMQTemplate rocketMQTemplate,
                              LifePulseProperties properties,
                              IdGenerator idGenerator) {
        this.outboxEventMapper = outboxEventMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
        this.idGenerator = idGenerator;
    }

    public void saveAndSend(String eventType, Long aggregateId, String topic, String payload, Runnable localFallback) {
        OutboxEvent event = savePending(eventType, aggregateId, topic, payload);
        if (!properties.getMq().isEnabled()) {
            localFallback.run();
            markSent(event.getId());
            return;
        }
        // A consumer must never observe an event for a database transaction that later rolls back.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAsync(event);
                }
            });
        } else {
            sendAsync(event);
        }
    }

    private void sendAsync(OutboxEvent event) {
        rocketMQTemplate.asyncSend(event.getTopic(), event.getPayload(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                markSent(event.getId());
            }

            @Override
            public void onException(Throwable throwable) {
                markFailed(event.getId(), throwable.getMessage());
            }
        });
    }

    public List<OutboxEvent> listRecent() {
        return outboxEventMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .orderByDesc(OutboxEvent::getCreatedAt)
                .last("limit 30"));
    }

    @Scheduled(fixedDelay = 5000)
    public void retryFailedEvents() {
        if (!properties.getMq().isEnabled()) {
            return;
        }
        List<OutboxEvent> events = outboxEventMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, PENDING, FAILED)
                .le(OutboxEvent::getNextRetryTime, LocalDateTime.now())
                .last("limit 20"));
        for (OutboxEvent event : events) {
            sendAsync(event);
        }
    }

    private OutboxEvent savePending(String eventType, Long aggregateId, String topic, String payload) {
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = new OutboxEvent();
        event.setId(idGenerator.nextId());
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setTopic(topic);
        event.setPayload(payload);
        event.setStatus(PENDING);
        event.setRetryCount(0);
        event.setNextRetryTime(now.plusSeconds(5));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        outboxEventMapper.insert(event);
        return event;
    }

    private void markSent(Long id) {
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, id)
                .set(OutboxEvent::getStatus, SENT)
                .set(OutboxEvent::getUpdatedAt, LocalDateTime.now()));
        log.info("outbox_sent eventId={}", id);
    }

    private void markFailed(Long id, String error) {
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, id)
                .set(OutboxEvent::getStatus, FAILED)
                .setSql("retry_count = retry_count + 1")
                .set(OutboxEvent::getNextRetryTime, LocalDateTime.now().plusSeconds(10))
                .set(OutboxEvent::getErrorMessage, error == null ? "send failed" : error)
                .set(OutboxEvent::getUpdatedAt, LocalDateTime.now()));
        log.warn("outbox_failed eventId={} error={}", id, error);
    }
}
