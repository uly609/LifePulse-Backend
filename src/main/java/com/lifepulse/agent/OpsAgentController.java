package com.lifepulse.agent;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class OpsAgentController {
    private final OpsAgentService opsAgentService;

    public OpsAgentController(OpsAgentService opsAgentService) {
        this.opsAgentService = opsAgentService;
    }

    @GetMapping("/diagnosis")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<OpsDiagnosis> diagnose() {
        return Result.success(opsAgentService.diagnose());
    }

    @GetMapping("/logs")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<List<OpsAgentService.LogEntry>> logs(@RequestParam(defaultValue = "") String keyword,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(opsAgentService.recentLogs(keyword, Math.max(1, Math.min(50, size))));
    }

    @GetMapping("/metrics")
    @RequireRole({"ADMIN", "MERCHANT"})
    public Result<Map<String, Object>> metrics() {
        return Result.success(opsAgentService.metricsSnapshot());
    }
}
