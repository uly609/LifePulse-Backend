package com.lifepulse.order;

import com.lifepulse.config.RocketTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lifepulse.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketTopics.ORDER_TIMEOUT,
        consumerGroup = "lifepulse-order-timeout-consumer-group"
)
public class OrderTimeoutConsumer implements RocketMQListener<Long> {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final DealOrderService orderService;

    public OrderTimeoutConsumer(DealOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(Long orderId) {
        try {
            orderService.timeoutCancel(orderId);
        } catch (Exception e) {
            log.warn("order_timeout_consume_failed orderId={} error={}", orderId, e.getMessage());
            throw e;
        }
    }
}
