package com.lifepulse.config.policy;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import com.lifepulse.config.dcc.DccValueRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/policies")
@RequireRole({"ADMIN"})
public class PolicyController {
    private final ObjectProvider<NacosPolicyService> nacosProvider;
    private final DccValueRegistry dccValueRegistry;

    public PolicyController(ObjectProvider<NacosPolicyService> nacosProvider, DccValueRegistry dccValueRegistry) {
        this.nacosProvider = nacosProvider;
        this.dccValueRegistry = dccValueRegistry;
    }

    @GetMapping public Result<RuntimePolicy.Snapshot> get() { return Result.success(RuntimePolicy.current()); }

    @PutMapping public Result<RuntimePolicy.Snapshot> update(@Valid @RequestBody PolicyRequest request) {
        RuntimePolicy.Snapshot current = RuntimePolicy.current();
        RuntimePolicy.Snapshot policy = new RuntimePolicy.Snapshot(
                request.shopCacheTtlSeconds(),
                request.seckillEnabled(),
                request.seckillRateLimitPerSecond() == null ? current.seckillRateLimitPerSecond() : request.seckillRateLimitPerSecond(),
                request.groupRateLimitPerSecond() == null ? current.groupRateLimitPerSecond() : request.groupRateLimitPerSecond(),
                request.orderTimeoutMinutes() == null ? current.orderTimeoutMinutes() : request.orderTimeoutMinutes());
        NacosPolicyService nacos = nacosProvider.getIfAvailable();
        if (nacos != null) {
            nacos.publish(policy);
        } else {
            RuntimePolicy.replace(policy);
            dccValueRegistry.refreshAll();
        }
        return Result.success(policy);
    }
    public record PolicyRequest(@Min(30) @Max(3600) long shopCacheTtlSeconds,
                                boolean seckillEnabled,
                                @Min(1) @Max(5000) Integer seckillRateLimitPerSecond,
                                @Min(1) @Max(5000) Integer groupRateLimitPerSecond,
                                @Min(1) @Max(120) Integer orderTimeoutMinutes) { }
}
