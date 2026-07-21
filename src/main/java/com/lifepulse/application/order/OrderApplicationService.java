package com.lifepulse.application.order;

import com.lifepulse.entity.DealOrder;
import com.lifepulse.order.DealOrderService;
import org.springframework.stereotype.Service;

import java.util.List;

/** Application layer: translates HTTP-facing order commands into domain operations. */
@Service
public class OrderApplicationService {
    private final DealOrderService orders;
    public OrderApplicationService(DealOrderService orders) { this.orders = orders; }
    public List<DealOrder> mine(Long userId) { return orders.myOrders(userId); }
    public void pay(Long orderId, Long userId) { orders.pay(orderId, userId); }
    public void cancel(Long orderId, Long userId) { orders.cancel(orderId, userId); }
    public void refund(Long orderId, Long userId) { orders.refund(orderId, userId); }
}
