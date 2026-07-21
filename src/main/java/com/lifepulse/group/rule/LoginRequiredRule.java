package com.lifepulse.group.rule;

import com.lifepulse.group.GroupEligibility;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class LoginRequiredRule implements GroupJoinRule {
    @Override
    public GroupEligibility evaluate(GroupJoinContext context) {
        if (context.userId() == null) {
            return new GroupEligibility(false, false, "登录后可参加拼团");
        }
        return null;
    }
}
