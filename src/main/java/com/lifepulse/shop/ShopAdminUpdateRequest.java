package com.lifepulse.shop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ShopAdminUpdateRequest(
        @Pattern(regexp = "OPEN|CLOSED") String status,
        @Min(0) @Max(100000) Integer hotScore
) {
}
