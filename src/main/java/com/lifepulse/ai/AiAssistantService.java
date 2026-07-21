package com.lifepulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.admin.AdminController;
import com.lifepulse.agent.OpsAgentService;
import com.lifepulse.auth.CurrentUser;
import com.lifepulse.auth.UserContext;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.LifePulseProperties;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.Shop;
import com.lifepulse.entity.Voucher;
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
        List<Shop> shops = List.of();
        List<Voucher> vouchers = List.of();
        List<DealOrder> orders = List.of();

        if (containsAny(normalized, "商户", "店", "咖啡", "轻食", "桌游", "shop")) {
            usedTools.add("list_shops");
            shops = shopService.listHot();
            context.add("商户数据：" + toJson(shops));
        }
        if (containsAny(normalized, "优惠券", "券", "秒杀", "抢券", "库存", "voucher")) {
            usedTools.add("list_vouchers");
            vouchers = voucherService.listSelling();
            context.add("优惠券数据：" + toJson(vouchers));
        }
        if (containsAny(normalized, "订单", "支付", "取消", "退款", "order")) {
            usedTools.add("my_orders");
            orders = orderService.myOrders(CurrentUser.resolve(null));
            context.add("当前用户订单：" + toJson(orders));
        }
        if (containsAny(normalized, "运营", "诊断", "积压", "outbox", "mq", "rocketmq", "风险", "监控")) {
            requireOperator();
            usedTools.add("ops_diagnosis");
            context.add("运营诊断：" + toJson(opsAgentService.diagnose()));
            context.add("运营统计：" + toJson(adminController.stats().data()));
        }
        if (context.isEmpty()) {
            usedTools.add("platform_summary");
            context.add("平台能力：商户查询、优惠券领取、订单查询、支付取消退款、评价互动和活动推荐。");
        }

        String contextText = String.join("\n", context);
        String fallbackAnswer = fallbackAnswer(question, normalized, usedTools, shops, vouchers, orders);
        String answer = callModel(question, contextText, fallbackAnswer);
        return new AiChatResponse(answer, usedTools, summarizeContext(usedTools));
    }

    private String callModel(String question, String contextText, String fallbackAnswer) {
        LifePulseProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            return fallbackAnswer;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", ai.getModel(),
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    你是 LifePulse 本地生活平台的智能客服和运营助手。
                                    面向普通用户时，只回答商户、优惠券、订单、支付、退款、评价、活动推荐等用户能理解的问题。
                                    不要主动提后端技术、接口、MCP、Outbox、RocketMQ、数据库、缓存等实现细节。
                                    只有用户明确询问运营诊断、系统风险、MQ、Outbox 或技术实现时，才用后端工程视角解释。
                                    回答必须基于提供的业务上下文，不要编造不存在的数据。
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
            return fallbackAnswer;
        }
    }

    private String fallbackAnswer(String question, String normalized, List<String> usedTools,
                                  List<Shop> shops, List<Voucher> vouchers, List<DealOrder> orders) {
        if (usedTools.contains("list_shops")) {
            List<Shop> matched = shops.stream()
                    .filter(shop -> !containsAny(normalized, "咖啡") || containsAny(shop.getName(), "咖啡", "烘焙")
                            || containsAny(shop.getCategory(), "咖啡", "甜品", "面包"))
                    .limit(3)
                    .toList();
            if (matched.isEmpty()) {
                matched = shops.stream().limit(3).toList();
            }
            if (!matched.isEmpty()) {
                StringBuilder answer = new StringBuilder("附近可以看看这几家：");
                for (Shop shop : matched) {
                    answer.append("\n").append(shop.getName())
                            .append("，").append(shop.getCategory())
                            .append("，地址在").append(shop.getAddress())
                            .append("，评分").append(shop.getAvgScore()).append("。");
                }
                return answer.append("\n你可以进入商户探店页查看详情和评价。").toString();
            }
        }
        if (usedTools.contains("list_vouchers") && !vouchers.isEmpty()) {
            StringBuilder answer = new StringBuilder("当前可领取的优惠券有：");
            vouchers.stream().limit(3).forEach(voucher -> answer.append("\n")
                    .append(voucher.getTitle())
                    .append("，秒杀价").append(voucher.getSalePrice())
                    .append("，剩余").append(voucher.getStock()).append("张。"));
            return answer.append("\n领取前需要先申请抢券资格。").toString();
        }
        if (usedTools.contains("my_orders")) {
            if (orders.isEmpty()) {
                return "你当前还没有订单。可以先去优惠券秒杀页领取券，再到我的订单里查看支付状态。";
            }
            StringBuilder answer = new StringBuilder("你最近的订单是：");
            orders.stream().limit(3).forEach(order -> answer.append("\n订单")
                    .append(order.getId())
                    .append("，金额").append(order.getAmount())
                    .append("，状态").append(order.getStatus()).append("。"));
            return answer.toString();
        }
        return "我可以帮你查询商户、优惠券和订单。你可以问我附近有什么店、有哪些优惠券，或者查看我的订单。";
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void requireOperator() {
        String role = UserContext.getRole();
        if (!"ADMIN".equals(role) && !"MERCHANT".equals(role)) {
            throw new BusinessException("当前账号无运营权限");
        }
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
