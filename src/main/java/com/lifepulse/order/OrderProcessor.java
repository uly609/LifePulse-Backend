package com.lifepulse.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.IdGenerator;
import com.lifepulse.entity.DealOrder;
import com.lifepulse.entity.Voucher;
import com.lifepulse.mapper.DealOrderMapper;
import com.lifepulse.mapper.VoucherMapper;
import com.lifepulse.voucher.OrderCreateMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderProcessor {
    private final DealOrderMapper orderMapper;
    private final VoucherMapper voucherMapper;
    private final IdGenerator idGenerator;
    private final OrderTimeoutProducer timeoutProducer;

    public OrderProcessor(DealOrderMapper orderMapper,
                          VoucherMapper voucherMapper,
                          IdGenerator idGenerator,
                          OrderTimeoutProducer timeoutProducer) {
        this.orderMapper = orderMapper;
        this.voucherMapper = voucherMapper;
        this.idGenerator = idGenerator;
        this.timeoutProducer = timeoutProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    public DealOrder createPendingOrder(OrderCreateMessage message) {
        DealOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<DealOrder>()
                .eq(DealOrder::getVoucherId, message.voucherId())
                .eq(DealOrder::getUserId, message.userId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        int stockUpdated = voucherMapper.update(null, new LambdaUpdateWrapper<Voucher>()
                .eq(Voucher::getId, message.voucherId())
                .gt(Voucher::getStock, 0)
                .setSql("stock = stock - 1")
                .set(Voucher::getUpdatedAt, LocalDateTime.now()));
        if (stockUpdated == 0) {
            throw new BusinessException("数据库库存不足");
        }

        LocalDateTime now = LocalDateTime.now();
        DealOrder order = new DealOrder();
        order.setId(idGenerator.nextId());
        order.setVoucherId(message.voucherId());
        order.setShopId(message.shopId());
        order.setUserId(message.userId());
        order.setAmount(message.amount());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        timeoutProducer.sendTimeoutAfterCommit(order);
        return order;
    }
}
