package com.lifepulse.domain.order;

import com.lifepulse.order.OrderStatus;

/** Domain rule: only a pending order may move to a terminal transaction state. */
public final class OrderStateMachine {
    private OrderStateMachine() { }
    public static boolean canPay(String status) { return OrderStatus.PENDING.equals(status); }
    public static boolean canCancel(String status) { return OrderStatus.PENDING.equals(status); }
    public static boolean canRefund(String status) { return OrderStatus.PAID.equals(status); }
}
