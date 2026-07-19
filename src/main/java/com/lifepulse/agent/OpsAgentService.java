package com.lifepulse.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.OutboxEvent;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.mapper.OutboxEventMapper;
import com.lifepulse.mapper.ShopReviewMapper;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.order.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpsAgentService {
    private final DealOrderMapper orderMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final VoucherMapper voucherMapper;
    private final ShopReviewMapper reviewMapper;

    public OpsAgentService(DealOrderMapper orderMapper,
                           OutboxEventMapper outboxEventMapper,
                           VoucherMapper voucherMapper,
                           ShopReviewMapper reviewMapper) {
        this.orderMapper = orderMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.voucherMapper = voucherMapper;
        this.reviewMapper = reviewMapper;
    }

    public OpsDiagnosis diagnose() {
        long pendingOutbox = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "PENDING"));
        long failedOutbox = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "FAILED"));
        long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PENDING));
        long paidOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PAID));
        long lowStockVouchers = voucherMapper.selectCount(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getStatus, "SELLING")
                .le(Voucher::getStock, 3));
        long reviews = reviewMapper.selectCount(null);

        List<String> findings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        evidence.add("待发送Outbox=" + pendingOutbox);
        evidence.add("发送失败Outbox=" + failedOutbox);
        evidence.add("待支付订单=" + pendingOrders);
        evidence.add("已支付订单=" + paidOrders);
        evidence.add("低库存券=" + lowStockVouchers);
        evidence.add("评价总量=" + reviews);

        if (failedOutbox > 0) {
            findings.add("发现失败消息，生产端到 RocketMQ 之间可能有短暂异常。");
            suggestions.add("先看 Outbox 错误信息和 RocketMQ Broker 状态，确认定时补偿是否继续推进。");
        }
        if (pendingOutbox > 10) {
            findings.add("Outbox 待发送较多，可能出现消息发送积压。");
            suggestions.add("检查 Broker 可用性、网络延迟和发送线程池，必要时扩容 MQ 节点。");
        }
        if (pendingOrders > paidOrders + 20) {
            findings.add("待支付订单明显偏多，可能存在支付转化低或超时取消不及时。");
            suggestions.add("检查延迟取消消息是否正常消费，并关注支付接口错误率。");
        }
        if (lowStockVouchers > 0) {
            findings.add("存在低库存活动，秒杀入口可能成为热点 Key。");
            suggestions.add("提前预热 Redis 库存 Key，必要时加前置限流和资格 Token。");
        }
        if (findings.isEmpty()) {
            findings.add("当前核心交易链路状态正常，没有明显积压或低库存风险。");
            suggestions.add("继续观察 RT、P95、QPS、Outbox 状态和订单最终一致性。");
        }

        String riskLevel = failedOutbox > 0 || pendingOutbox > 20 ? "HIGH" :
                pendingOutbox > 10 || pendingOrders > paidOrders + 20 || lowStockVouchers > 0 ? "MEDIUM" : "LOW";
        String summary = "运营诊断完成：" + LocalDateTime.now() + "，当前风险等级 " + riskLevel + "。";
        return new OpsDiagnosis(riskLevel, summary, findings, suggestions, evidence);
    }
}
