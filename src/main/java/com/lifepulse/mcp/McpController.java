package com.lifepulse.mcp;

import com.lifepulse.admin.AdminController;
import com.lifepulse.agent.OpsAgentService;
import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.Result;
import com.lifepulse.order.DealOrderService;
import com.lifepulse.shop.ShopService;
import com.lifepulse.voucher.VoucherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class McpController {
    private final ShopService shopService;
    private final VoucherService voucherService;
    private final DealOrderService orderService;
    private final AdminController adminController;
    private final OpsAgentService opsAgentService;

    public McpController(ShopService shopService,
                         VoucherService voucherService,
                         DealOrderService orderService,
                         AdminController adminController,
                         OpsAgentService opsAgentService) {
        this.shopService = shopService;
        this.voucherService = voucherService;
        this.orderService = orderService;
        this.adminController = adminController;
        this.opsAgentService = opsAgentService;
    }

    @GetMapping("/api/mcp/tools")
    public Result<List<McpTool>> listToolsForBrowser() {
        return Result.success(tools());
    }

    @PostMapping("/mcp")
    public McpResponse handle(@RequestBody McpRequest request) {
        try {
            if ("tools/list".equals(request.method())) {
                return McpResponse.success(request.id(), Map.of("tools", tools()));
            }
            if ("tools/call".equals(request.method())) {
                return McpResponse.success(request.id(), callTool(request.params()));
            }
            return McpResponse.error(request.id(), -32601, "未知MCP方法：" + request.method());
        } catch (Exception e) {
            return McpResponse.error(request.id(), -32000, e.getMessage());
        }
    }

    private List<McpTool> tools() {
        return List.of(
                new McpTool("list_shops", "查询热门商户列表", objectSchema()),
                new McpTool("list_vouchers", "查询正在售卖的优惠券", objectSchema()),
                new McpTool("my_orders", "查询当前登录用户的订单", objectSchema()),
                new McpTool("admin_stats", "查询运营统计数据", objectSchema()),
                new McpTool("ops_diagnosis", "诊断订单、库存和Outbox链路风险", objectSchema())
        );
    }

    private Object callTool(Map<String, Object> params) {
        if (params == null) {
            throw new BusinessException("MCP参数不能为空");
        }
        String name = String.valueOf(params.get("name"));
        return switch (name) {
            case "list_shops" -> shopService.listHot();
            case "list_vouchers" -> voucherService.listSelling();
            case "my_orders" -> orderService.myOrders(CurrentUser.resolve(null));
            case "admin_stats" -> adminController.stats().data();
            case "ops_diagnosis" -> opsAgentService.diagnose();
            default -> throw new BusinessException("未知工具：" + name);
        };
    }

    private Map<String, Object> objectSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }
}
