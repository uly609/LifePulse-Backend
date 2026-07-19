package com.lifepulse.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        Long userId,
        @Min(value = 1, message = "评分最低为1")
        @Max(value = 5, message = "评分最高为5")
        Integer score,
        @NotBlank(message = "评价内容不能为空") String content
) {
}
