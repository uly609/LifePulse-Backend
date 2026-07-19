package com.lifepulse.review;

import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shops/{shopId}/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public Result<Void> publish(@PathVariable Long shopId, @Valid @RequestBody ReviewRequest request) {
        reviewService.publish(shopId, CurrentUser.resolve(request.userId()), request);
        return Result.success(null);
    }
}
