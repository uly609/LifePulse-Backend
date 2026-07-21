package com.lifepulse.benchmark;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.Result;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.order.OrderProcessor;
import com.lifepulse.voucher.OrderCreateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.config.RocketTopics;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exists only in the benchmark profile to measure in-request order persistence. */
@Profile("benchmark")
@RestController
@RequestMapping("/api/internal/benchmark/orders")
@RequireRole({"ADMIN"})
public class SyncOrderBenchmarkController {
    private final VoucherMapper vouchers;
    private final OrderProcessor orders;
    private final RocketMQTemplate rocketMQ;
    private final ObjectMapper objectMapper;
    public SyncOrderBenchmarkController(VoucherMapper vouchers, OrderProcessor orders, RocketMQTemplate rocketMQ, ObjectMapper objectMapper) {
        this.vouchers = vouchers; this.orders = orders; this.rocketMQ = rocketMQ; this.objectMapper = objectMapper;
    }

    @PostMapping("/sync")
    public Result<Void> syncCreate(@RequestParam Long voucherId, @RequestParam Long userId) {
        Voucher voucher = vouchers.selectById(voucherId);
        if (voucher == null) throw new BusinessException("优惠券不存在");
        orders.createPendingOrder(new OrderCreateMessage(voucher.getId(), voucher.getShopId(), userId, voucher.getSalePrice()));
        return Result.success(null);
    }

    @PostMapping("/sync-send")
    public Result<Void> syncSend(@RequestParam Long voucherId, @RequestParam Long userId) throws Exception {
        Voucher voucher = vouchers.selectById(voucherId);
        if (voucher == null) throw new BusinessException("优惠券不存在");
        String payload = objectMapper.writeValueAsString(new OrderCreateMessage(voucher.getId(), voucher.getShopId(), userId, voucher.getSalePrice()));
        // Deliberately waits only for the Broker acknowledgement, never for consumer persistence.
        rocketMQ.syncSend(RocketTopics.ORDER_CREATE, payload, 3000);
        return Result.success(null);
    }

    @PostMapping("/async-send")
    public Result<Void> asyncSend(@RequestParam Long voucherId, @RequestParam Long userId) throws Exception {
        Voucher voucher = vouchers.selectById(voucherId);
        if (voucher == null) throw new BusinessException("优惠券不存在");
        String payload = objectMapper.writeValueAsString(new OrderCreateMessage(voucher.getId(), voucher.getShopId(), userId, voucher.getSalePrice()));
        rocketMQ.asyncSend(RocketTopics.ORDER_CREATE, payload, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(Throwable throwable) {
            }
        });
        return Result.success(null);
    }
}
