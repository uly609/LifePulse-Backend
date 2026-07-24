package com.lifepulse.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.dcc.DccValue;
import com.lifepulse.domain.order.OrderStateMachine;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.voucher.VoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DealOrderService {
    private static final Logger log = LoggerFactory.getLogger(DealOrderService.class);

    private final DealOrderMapper orderMapper;
    private final VoucherService voucherService;
    @DccValue("order-timeout-minutes")
    private volatile int orderTimeoutMinutes = 15;

    public DealOrderService(DealOrderMapper orderMapper, VoucherService voucherService) {
        this.orderMapper = orderMapper;
        this.voucherService = voucherService;
    }

    public List<DealOrder> myOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getUserId, userId)
                .orderByDesc(DealOrder::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId, Long userId) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DealOrder>()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getUserId, userId)
                .eq(DealOrder::getStatus, OrderStatus.PENDING)
                .set(DealOrder::getStatus, OrderStatus.PAID)
                .set(DealOrder::getPaidAt, LocalDateTime.now())
                .set(DealOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，不能支付");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, Long userId) {
        DealOrder order = requireOrder(orderId, userId);
        if (!OrderStateMachine.canCancel(order.getStatus())) {
            throw new BusinessException("订单状态已变化，不能取消");
        }
        if (!cancelPendingOrder(order, true)) {
            throw new BusinessException("订单状态已变化，不能取消");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId, Long userId) {
        DealOrder order = requireOrder(orderId, userId);
        if (!OrderStateMachine.canRefund(order.getStatus())) {
            throw new BusinessException("订单状态已变化，不能退款");
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DealOrder>()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getUserId, userId)
                .eq(DealOrder::getStatus, OrderStatus.PAID)
                .set(DealOrder::getStatus, OrderStatus.REFUNDED)
                .set(DealOrder::getRefundedAt, LocalDateTime.now())
                .set(DealOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，不能退款");
        }
        voucherService.restoreDbStock(order.getVoucherId());
    }

    @Scheduled(fixedDelay = 10_000L)
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredPendingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(orderTimeoutMinutes);
        List<DealOrder> expiredOrders = orderMapper.selectList(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getStatus, OrderStatus.PENDING)
                .gt(DealOrder::getShopId, 0)
                .le(DealOrder::getCreatedAt, deadline)
                .last("limit 50"));
        for (DealOrder order : expiredOrders) {
            cancelPendingOrder(order, false);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void timeoutCancel(Long orderId) {
        DealOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getShopId() == null || order.getShopId() <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(orderTimeoutMinutes);
        if (order.getCreatedAt().isAfter(deadline)) {
            return;
        }
        cancelPendingOrder(order, false);
    }

    private boolean cancelPendingOrder(DealOrder order, boolean checkUser) {
        LambdaUpdateWrapper<DealOrder> wrapper = new LambdaUpdateWrapper<DealOrder>()
                .eq(DealOrder::getId, order.getId())
                .eq(DealOrder::getStatus, OrderStatus.PENDING)
                .set(DealOrder::getStatus, OrderStatus.CANCELED)
                .set(DealOrder::getCanceledAt, LocalDateTime.now())
                .set(DealOrder::getUpdatedAt, LocalDateTime.now());
        if (checkUser) {
            wrapper.eq(DealOrder::getUserId, order.getUserId());
        }
        int updated = orderMapper.update(null, wrapper);
        if (updated == 0) {
            return false;
        }
        voucherService.restoreDbStock(order.getVoucherId());
        voucherService.rollbackRedisQualification(order.getVoucherId(), order.getUserId());
        log.info("order_canceled_and_stock_restored orderId={} userId={} voucherId={} timeout={}",
                order.getId(), order.getUserId(), order.getVoucherId(), !checkUser);
        return true;
    }

    private DealOrder requireOrder(Long orderId, Long userId) {
        DealOrder order = orderMapper.selectOne(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getUserId, userId)
                .last("limit 1"));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }
}
