package com.lifepulse.group.rule;

import com.lifepulse.group.GroupEligibility;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupJoinRuleChain {
    private final List<GroupJoinRule> rules;

    public GroupJoinRuleChain(List<GroupJoinRule> rules) {
        this.rules = rules;
    }

    public GroupEligibility evaluate(GroupJoinContext context) {
        for (GroupJoinRule rule : rules) {
            GroupEligibility result = rule.evaluate(context);
            if (result != null) {
                return result;
            }
        }
        return new GroupEligibility(true, false, "可以参加");
    }
}
