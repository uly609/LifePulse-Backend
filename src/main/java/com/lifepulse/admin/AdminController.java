package com.lifepulse.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.OutboxEvent;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.mapper.OutboxEventMapper;
import com.lifepulse.mapper.ShopMapper;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.order.OrderStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ShopMapper shopMapper;
    private final VoucherMapper voucherMapper;
    private final DealOrderMapper orderMapper;
    private final OutboxEventMapper outboxEventMapper;

    public AdminController(ShopMapper shopMapper,
                           VoucherMapper voucherMapper,
                           DealOrderMapper orderMapper,
                           OutboxEventMapper outboxEventMapper) {
        this.shopMapper = shopMapper;
        this.voucherMapper = voucherMapper;
        this.orderMapper = orderMapper;
        this.outboxEventMapper = outboxEventMapper;
    }

    @GetMapping("/stats")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<AdminStats> stats() {
        long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PENDING));
        long paidOrders = orderMapper.selectCount(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PAID));
        long outboxPending = outboxEventMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .ne(OutboxEvent::getStatus, "SENT"));
        return Result.success(new AdminStats(
                shopMapper.selectCount(null),
                voucherMapper.selectCount(null),
                orderMapper.selectCount(null),
                pendingOrders,
                paidOrders,
                outboxPending
        ));
    }
}
