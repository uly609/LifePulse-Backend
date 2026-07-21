package com.lifepulse.group;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GroupActivityRequest(
        @NotNull Long voucherId, @NotBlank String title, @NotBlank String description,
        @Min(2) Integer requiredSize, @NotNull BigDecimal groupPrice, @Min(1) Integer totalStock,
        @NotBlank String allowedRole, @NotNull LocalDateTime endTime
) {}
