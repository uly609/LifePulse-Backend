package com.lifepulse.config.policy;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/admin/policies")
@RequireRole({"ADMIN"})
@ConditionalOnProperty(prefix = "lifepulse.nacos", name = "enabled", havingValue = "true")
public class PolicyController {
    private final NacosPolicyService nacos;
    public PolicyController(NacosPolicyService nacos) { this.nacos = nacos; }
    @GetMapping public Result<RuntimePolicy.Snapshot> get() { return Result.success(nacos.current()); }
    @PutMapping public Result<RuntimePolicy.Snapshot> update(@Valid @RequestBody PolicyRequest request) {
        RuntimePolicy.Snapshot policy = new RuntimePolicy.Snapshot(request.shopCacheTtlSeconds(), request.seckillEnabled());
        nacos.publish(policy); return Result.success(policy);
    }
    public record PolicyRequest(@Min(30) @Max(3600) long shopCacheTtlSeconds, boolean seckillEnabled) { }
}
