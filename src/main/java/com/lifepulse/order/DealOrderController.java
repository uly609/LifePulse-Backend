package com.lifepulse.order;

import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.Result;
import com.lifepulse.entity.DealOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class DealOrderController {
    private final DealOrderService orderService;

    public DealOrderController(DealOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/me")
    public Result<List<DealOrder>> myOrders(@RequestParam(required = false) Long userId) {
        return Result.success(orderService.myOrders(CurrentUser.resolve(userId)));
    }

    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId, @RequestParam(required = false) Long userId) {
        orderService.pay(orderId, CurrentUser.resolve(userId));
        return Result.success(null);
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId, @RequestParam(required = false) Long userId) {
        orderService.cancel(orderId, CurrentUser.resolve(userId));
        return Result.success(null);
    }

    @PostMapping("/{orderId}/refund")
    public Result<Void> refund(@PathVariable Long orderId, @RequestParam(required = false) Long userId) {
        orderService.refund(orderId, CurrentUser.resolve(userId));
        return Result.success(null);
    }
}
