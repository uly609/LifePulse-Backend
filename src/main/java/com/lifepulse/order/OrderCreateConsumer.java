package com.lifepulse.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.config.RocketTopics;
import com.lifepulse.voucher.OrderCreateMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lifepulse.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = RocketTopics.ORDER_CREATE,
        consumerGroup = "lifepulse-order-create-consumer-group"
)
public class OrderCreateConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final OrderProcessor orderProcessor;

    public OrderCreateConsumer(ObjectMapper objectMapper, OrderProcessor orderProcessor) {
        this.objectMapper = objectMapper;
        this.orderProcessor = orderProcessor;
    }

    @Override
    public void onMessage(String payload) {
        try {
            OrderCreateMessage message = objectMapper.readValue(payload, OrderCreateMessage.class);
            orderProcessor.createPendingOrder(message);
        } catch (Exception e) {
            throw new IllegalStateException("订单创建消息消费失败，交给 RocketMQ 重试", e);
        }
    }
}
