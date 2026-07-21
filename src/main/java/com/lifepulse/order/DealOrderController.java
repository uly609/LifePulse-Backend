package com.lifepulse.order;

import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.Result;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.application.order.OrderApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class DealOrderController {
    private final OrderApplicationService orderService;

    public DealOrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/me")
    public Result<List<DealOrder>> myOrders() {
        return Result.success(orderService.mine(CurrentUser.resolve(null)));
    }

    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId) {
        orderService.pay(orderId, CurrentUser.resolve(null));
        return Result.success(null);
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId, CurrentUser.resolve(null));
        return Result.success(null);
    }

    @PostMapping("/{orderId}/refund")
    public Result<Void> refund(@PathVariable Long orderId) {
        orderService.refund(orderId, CurrentUser.resolve(null));
        return Result.success(null);
    }
}
