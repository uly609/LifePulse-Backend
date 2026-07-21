package com.lifepulse.group;

import com.lifepulse.auth.CurrentUser;
import com.lifepulse.auth.RequireRole;
import com.lifepulse.auth.UserContext;
import com.lifepulse.common.Result;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.GroupTeam;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService service;
    public GroupController(GroupService service) { this.service = service; }
    @GetMapping("/activities") public Result<List<GroupActivity>> activities() { return Result.success(service.activities()); }
    @GetMapping("/activities/{id}") public Result<GroupView> detail(@PathVariable Long id) { return Result.success(service.detail(id, UserContext.getUserId(), UserContext.getRole())); }
    @GetMapping("/me") public Result<List<MyGroupView>> mine() { return Result.success(service.mine(CurrentUser.resolve(null))); }
    @PostMapping("/activities/{id}") public Result<GroupTeam> create(@PathVariable Long id) { return Result.success(service.create(id, CurrentUser.resolve(null), UserContext.getRole())); }
    @PostMapping("/{id}/join") public Result<GroupTeam> join(@PathVariable Long id) { return Result.success(service.join(id, CurrentUser.resolve(null), UserContext.getRole())); }
    @PostMapping("/orders/{id}/pay") public Result<Void> pay(@PathVariable Long id) { service.pay(id, CurrentUser.resolve(null)); return Result.success(null); }
    @PostMapping("/admin/activities") @RequireRole({"ADMIN", "MERCHANT"}) public Result<GroupActivity> configure(@Valid @RequestBody GroupActivityRequest request) { return Result.success(service.createActivity(request)); }
    @PostMapping("/admin/activities/{id}/status") @RequireRole({"ADMIN", "MERCHANT"}) public Result<Void> status(@PathVariable Long id, @RequestParam String value) { service.setActivityStatus(id, value); return Result.success(null); }
    @PostMapping("/admin/groups/{id}/fail") @RequireRole({"ADMIN", "MERCHANT"}) public Result<Void> fail(@PathVariable Long id) { service.failGroup(id); return Result.success(null); }
}
