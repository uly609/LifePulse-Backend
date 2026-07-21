package com.lifepulse.group.rule;

import com.lifepulse.group.GroupEligibility;

public interface GroupJoinRule {
    GroupEligibility evaluate(GroupJoinContext context);
}
