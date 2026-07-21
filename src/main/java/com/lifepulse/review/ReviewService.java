package com.lifepulse.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.common.IdGenerator;
import com.lifepulse.entity.Shop;
import com.lifepulse.entity.ShopReview;
import com.lifepulse.mapper.ShopMapper;
import com.lifepulse.mapper.ShopReviewMapper;
import com.lifepulse.shop.ShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class ReviewService {
    private final ShopReviewMapper reviewMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;
    private final IdGenerator idGenerator;

    public ReviewService(ShopReviewMapper reviewMapper,
                         ShopMapper shopMapper,
                         ShopService shopService,
                         IdGenerator idGenerator) {
        this.reviewMapper = reviewMapper;
        this.shopMapper = shopMapper;
        this.shopService = shopService;
        this.idGenerator = idGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long shopId, Long userId, ReviewRequest request) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException("商户不存在");
        }
        Long reviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<ShopReview>()
                .eq(ShopReview::getShopId, shopId)
                .eq(ShopReview::getUserId, userId));
        if (reviewCount > 0) {
            throw new BusinessException("你已评价过该商户，请勿重复发布");
        }
        int oldCount = shop.getCommentCount();
        BigDecimal oldTotal = shop.getAvgScore().multiply(BigDecimal.valueOf(oldCount));
        BigDecimal newAvg = oldTotal.add(BigDecimal.valueOf(request.score()))
                .divide(BigDecimal.valueOf(oldCount + 1L), 2, RoundingMode.HALF_UP);

        ShopReview review = new ShopReview();
        review.setId(idGenerator.nextId());
        review.setShopId(shopId);
        review.setUserId(userId);
        review.setScore(request.score());
        review.setContent(request.content());
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .set(Shop::getAvgScore, newAvg)
                .setSql("comment_count = comment_count + 1")
                .setSql("hot_score = hot_score + 10")
                .set(Shop::getUpdatedAt, LocalDateTime.now()));
        shopService.evict(shopId);
    }
}
