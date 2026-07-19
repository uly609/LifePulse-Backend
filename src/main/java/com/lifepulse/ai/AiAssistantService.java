package com.lifepulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.admin.AdminController;
import com.lifepulse.agent.OpsAgentService;
import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.order.DealOrderService;
import com.lifepulse.shop.ShopService;
import com.lifepulse.voucher.VoucherService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiAssistantService {
    private final ShopService shopService;
    private final VoucherService voucherService;
    private final DealOrderService orderService;
    private final AdminController adminController;
    private final OpsAgentService opsAgentService;
    private final LifePulseProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiAssistantService(ShopService shopService,
                              VoucherService voucherService,
                              DealOrderService orderService,
                              AdminController adminController,
                              OpsAgentService opsAgentService,
                              LifePulseProperties properties,
                              ObjectMapper objectMapper,
                              RestClient.Builder restClientBuilder) {
        this.shopService = shopService;
        this.voucherService = voucherService;
        this.orderService = orderService;
        this.adminController = adminController;
        this.opsAgentService = opsAgentService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public AiChatResponse chat(String question) {
        List<String> usedTools = new ArrayList<>();
        List<String> context = new ArrayList<>();
        String normalized = question.toLowerCase();

        if (containsAny(normalized, "商户", "店", "咖啡", "轻食", "桌游", "shop")) {
            usedTools.add("list_shops");
            context.add("商户数据：" + toJson(shopService.listHot()));
        }
        if (containsAny(normalized, "优惠券", "券", "秒杀", "抢券", "库存", "voucher")) {
            usedTools.add("list_vouchers");
            context.add("优惠券数据：" + toJson(voucherService.listSelling()));
        }
        if (containsAny(normalized, "订单", "支付", "取消", "退款", "order")) {
            usedTools.add("my_orders");
            context.add("当前用户订单：" + toJson(orderService.myOrders(CurrentUser.resolve(null))));
        }
        if (containsAny(normalized, "运营", "诊断", "积压", "outbox", "mq", "rocketmq", "风险", "监控")) {
            usedTools.add("ops_diagnosis");
            context.add("运营诊断：" + toJson(opsAgentService.diagnose()));
            context.add("运营统计：" + toJson(adminController.stats().data()));
        }
        if (context.isEmpty()) {
            usedTools.add("platform_summary");
            context.add("平台能力：商户查询、优惠券秒杀、订单支付取消退款、评价互动、Outbox可靠消息、RocketMQ异步削峰、MCP工具和运营诊断。");
        }

        String contextText = String.join("\n", context);
        String answer = callModel(question, contextText);
        return new AiChatResponse(answer, usedTools, summarizeContext(usedTools));
    }

    private String callModel(String question, String contextText) {
        LifePulseProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            return "AI API Key 还没配置。当前已完成业务工具检索，拿到的上下文是：\n" + contextText;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", ai.getModel(),
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    你是 LifePulse 本地生活平台的智能客服和运营助手。
                                    回答必须基于提供的业务上下文，不要编造不存在的数据。
                                    如果用户问抢券、订单、库存、Outbox、MQ，要用后端工程视角解释清楚。
                                    """),
                            Map.of("role", "user", "content", "用户问题：" + question + "\n\n业务上下文：\n" + contextText)
                    )
            );
            String response = restClient.post()
                    .uri(ai.getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException("AI返回为空");
            }
            return content.asText();
        } catch (Exception e) {
            return "AI API 调用失败：" + e.getMessage() + "\n\n已检索到的业务上下文：\n" + contextText;
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String summarizeContext(List<String> usedTools) {
        return "本次调用工具：" + String.join("、", usedTools);
    }
}
