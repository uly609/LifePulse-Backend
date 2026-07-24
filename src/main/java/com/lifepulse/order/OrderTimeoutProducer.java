package com.lifepulse.order;

import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.config.RocketTopics;
import com.lifepulse.config.policy.RuntimePolicy;
import com.lifepulse.entity.DealOrder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderTimeoutProducer {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutProducer.class);

    private final LifePulseProperties properties;
    private final RocketMQTemplate rocketMQTemplate;

    public OrderTimeoutProducer(LifePulseProperties properties, ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.properties = properties;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
    }

    public void sendTimeoutAfterCommit(DealOrder order) {
        if (!properties.getMq().isEnabled() || rocketMQTemplate == null
                || order.getShopId() == null || order.getShopId() <= 0) {
            return;
        }
        Runnable sendTask = () -> sendTimeoutMessage(order.getId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
            return;
        }
        sendTask.run();
    }

    private void sendTimeoutMessage(Long orderId) {
        long delaySeconds = Math.max(1, RuntimePolicy.current().orderTimeoutMinutes()) * 60L;
        try {
            rocketMQTemplate.syncSendDelayTimeSeconds(RocketTopics.ORDER_TIMEOUT, orderId, delaySeconds);
            log.info("order_timeout_message_sent orderId={} delaySeconds={}", orderId, delaySeconds);
        } catch (Exception e) {
            // Scheduled timeout scanner is the safety net if delayed message delivery is temporarily unavailable.
            log.warn("order_timeout_message_send_failed orderId={} error={}", orderId, e.getMessage());
        }
    }
}
