package com.lifepulse.admin;

public record AdminStats(
        long shops,
        long vouchers,
        long orders,
        long pendingOrders,
        long paidOrders,
        long outboxPending
) {
}
