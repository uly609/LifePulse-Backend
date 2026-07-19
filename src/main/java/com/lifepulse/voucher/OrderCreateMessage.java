package com.lifepulse.voucher;

import java.math.BigDecimal;

public record OrderCreateMessage(
        Long voucherId,
        Long shopId,
        Long userId,
        BigDecimal amount
) {
}
