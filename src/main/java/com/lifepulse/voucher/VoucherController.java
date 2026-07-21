package com.lifepulse.voucher;

import com.lifepulse.auth.CurrentUser;
import com.lifepulse.common.Result;
import com.lifepulse.entity.Voucher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public Result<List<Voucher>> listSelling() {
        return Result.success(voucherService.listSelling());
    }

    @PostMapping("/{voucherId}/qualification")
    public Result<QualificationResponse> applyQualification(@PathVariable Long voucherId) {
        return Result.success(voucherService.applyQualification(voucherId, CurrentUser.resolve(null)));
    }

    @PostMapping("/{voucherId}/seckill")
    public Result<EnrollResponse> seckill(@PathVariable Long voucherId,
                                          @RequestParam String qualificationToken) {
        return Result.success(voucherService.seckill(voucherId, CurrentUser.resolve(null), qualificationToken));
    }
}
