package com.lifepulse.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.domain.order.OrderStateMachine;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.voucher.VoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DealOrderService {
    private final DealOrderMapper orderMapper;
    private final VoucherService voucherService;

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
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<DealOrder>()
                .eq(DealOrder::getId, orderId)
                .eq(DealOrder::getUserId, userId)
                .eq(DealOrder::getStatus, OrderStatus.PENDING)
                .set(DealOrder::getStatus, OrderStatus.CANCELED)
                .set(DealOrder::getCanceledAt, LocalDateTime.now())
                .set(DealOrder::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，不能取消");
        }
        voucherService.restoreDbStock(order.getVoucherId());
        voucherService.rollbackRedisQualification(order.getVoucherId(), userId);
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
