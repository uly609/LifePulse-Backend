package com.lifepulse.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.OutboxEvent;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.Voucher;
import com.lifepulse.group.GroupParticipationBitmapService;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.mapper.GroupActivityMapper;
import com.lifepulse.mapper.OutboxEventMapper;
import com.lifepulse.mapper.ShopReviewMapper;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.order.OrderStatus;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpsAgentService {
    private final DealOrderMapper orderMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final VoucherMapper voucherMapper;
    private final ShopReviewMapper reviewMapper;
    private final GroupActivityMapper activityMapper;
    private final GroupParticipationBitmapService bitmapService;
    private final LifePulseProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpsAgentService(DealOrderMapper orderMapper,
                           OutboxEventMapper outboxEventMapper,
                           VoucherMapper voucherMapper,
                           ShopReviewMapper reviewMapper,
                           GroupActivityMapper activityMapper,
                           GroupParticipationBitmapService bitmapService,
                           LifePulseProperties properties,
                           ObjectMapper objectMapper,
                           RestClient.Builder restClientBuilder) {
        this.orderMapper = orderMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.voucherMapper = voucherMapper;
        this.reviewMapper = reviewMapper;
        this.activityMapper = activityMapper;
        this.bitmapService = bitmapService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public OpsDiagnosis diagnose() {
        long pendingOutbox = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "PENDING"));
        long failedOutbox = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "FAILED"));
        long deadLetterOutbox = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "DEAD_LETTER"));
        long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PENDING));
        long paidOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PAID));
        long lowStockVouchers = voucherMapper.selectCount(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getStatus, "SELLING")
                .le(Voucher::getStock, 3));
        long reviews = reviewMapper.selectCount(null);
        GroupActivity latestActivity = activityMapper.selectList(new LambdaQueryWrapper<GroupActivity>()
                .eq(GroupActivity::getStatus, "ONGOING")
                .orderByDesc(GroupActivity::getCreatedAt)
                .last("limit 1")).stream().findFirst().orElse(null);
        long bitmapJoined = latestActivity == null ? 0L : bitmapService.approximateJoinedCount(latestActivity.getId());

        List<String> findings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        evidence.add("待发送Outbox=" + pendingOutbox);
        evidence.add("发送失败Outbox=" + failedOutbox);
        evidence.add("死信Outbox=" + deadLetterOutbox);
        evidence.add("待支付订单=" + pendingOrders);
        evidence.add("已支付订单=" + paidOrders);
        evidence.add("低库存券=" + lowStockVouchers);
        evidence.add("评价总量=" + reviews);
        if (latestActivity != null) {
            evidence.add("拼团Bitmap近似参与数(activityId=" + latestActivity.getId() + ")=" + bitmapJoined);
        }

        if (deadLetterOutbox > 0) {
            findings.add("存在死信消息，说明生产端消息超过最大重试次数仍未发送成功。");
            suggestions.add("优先按 Outbox 死信记录排查 Broker、网络和消息体，再人工重放或补偿业务。");
        }
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
        if (bitmapJoined > 0 && latestActivity != null) {
            suggestions.add("拼团活动已产生参与痕迹，可以结合 Bitmap 和 MySQL 订单状态继续观察成团率。");
        }
        if (findings.isEmpty()) {
            findings.add("当前核心交易链路状态正常，没有明显积压或低库存风险。");
            suggestions.add("继续观察 RT、P95、QPS、Outbox 状态和订单最终一致性。");
        }

        String riskLevel = deadLetterOutbox > 0 || failedOutbox > 0 || pendingOutbox > 20 ? "HIGH" :
                pendingOutbox > 10 || pendingOrders > paidOrders + 20 || lowStockVouchers > 0 ? "MEDIUM" : "LOW";
        String summary = "运营诊断完成：" + LocalDateTime.now() + "，当前风险等级 " + riskLevel + "。";
        return new OpsDiagnosis(riskLevel, summary, findings, suggestions, evidence);
    }

    public List<LogEntry> recentLogs(String keyword, int size) {
        String esUrl = properties.getObservability().getElasticsearchUrl();
        String index = properties.getObservability().getLogIndexPattern();
        Map<String, Object> body = keyword == null || keyword.isBlank()
                ? Map.of("size", size, "sort", List.of(Map.of("@timestamp", Map.of("order", "desc"))))
                : Map.of(
                        "size", size,
                        "sort", List.of(Map.of("@timestamp", Map.of("order", "desc"))),
                        "query", Map.of("simple_query_string", Map.of(
                                "query", keyword,
                                "fields", List.of("message", "logger", "traceId", "userId")
                        )));
        try {
            String response = restClient.post()
                    .uri(esUrl + "/" + index + "/_search")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode hits = root.path("hits").path("hits");
            List<LogEntry> entries = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                entries.add(new LogEntry(
                        source.path("timestamp").asText(""),
                        source.path("level").asText(""),
                        source.path("traceId").asText(""),
                        source.path("userId").asText(""),
                        source.path("logger").asText(""),
                        source.path("message").asText("")
                ));
            }
            return entries;
        } catch (Exception e) {
            return List.of(new LogEntry("", "ERROR", "", "", "OpsAgentService",
                    "日志查询失败：" + e.getMessage()));
        }
    }

    public Map<String, Object> metricsSnapshot() {
        return Map.of(
                "processCpuUsage", prometheusValue("process_cpu_usage"),
                "heapUsedBytes", prometheusValue("jvm_memory_used_bytes{area=\"heap\"}"),
                "jvmGcPauseSeconds", prometheusValue("jvm_gc_pause_seconds_sum"),
                "httpRequests", prometheusValue("http_server_requests_seconds_count")
        );
    }

    private String prometheusValue(String query) {
        try {
            String response = restClient.get()
                    .uri(properties.getObservability().getPrometheusUrl() + "/api/v1/query?query=" + query)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("data").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return "0";
            }
            return result.get(0).path("value").path(1).asText("0");
        } catch (Exception e) {
            return "0";
        }
    }

    public record LogEntry(String timestamp, String level, String traceId, String userId, String logger, String message) {}
}
