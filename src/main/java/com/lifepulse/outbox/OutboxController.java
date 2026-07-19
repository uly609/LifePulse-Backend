package com.lifepulse.outbox;

import com.lifepulse.common.Result;
import com.lifepulse.auth.RequireRole;
import com.lifepulse.entity.OutboxEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outbox")
public class OutboxController {
    private final OutboxEventService outboxEventService;

    public OutboxController(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @GetMapping
    @RequireRole({"ADMIN"})
    public Result<List<OutboxEvent>> listRecent() {
        return Result.success(outboxEventService.listRecent());
    }
}
