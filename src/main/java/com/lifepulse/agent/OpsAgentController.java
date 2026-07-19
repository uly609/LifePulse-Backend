package com.lifepulse.agent;

import com.lifepulse.auth.RequireRole;
import com.lifepulse.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
