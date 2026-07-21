package com.lifepulse.group.rule;

import com.lifepulse.group.GroupEligibility;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class RoleMatchRule implements GroupJoinRule {
    @Override
    public GroupEligibility evaluate(GroupJoinContext context) {
        String allowedRole = context.activity().getAllowedRole();
        if (!"ALL".equals(allowedRole) && !allowedRole.equals(context.role())) {
            return new GroupEligibility(false, false, "当前账号不满足活动参与资格");
        }
        return null;
    }
}
