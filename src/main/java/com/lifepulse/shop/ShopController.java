package com.lifepulse.shop;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import com.lifepulse.entity.Shop;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
public class ShopController {
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public Result<List<Shop>> listHot() {
        return Result.success(shopService.listHot());
    }

    @GetMapping("/{id}")
    public Result<Shop> detail(@PathVariable Long id) {
        return Result.success(shopService.detail(id));
    }

    @PutMapping("/{id}")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<Shop> updateByAdmin(@PathVariable Long id, @Valid @RequestBody ShopAdminUpdateRequest request) {
        return Result.success(shopService.updateByAdmin(id, request));
    }
}
