package com.lifepulse.group.rule;

import com.lifepulse.group.GroupEligibility;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class StockAvailableRule implements GroupJoinRule {
    @Override
    public GroupEligibility evaluate(GroupJoinContext context) {
        if (context.activity().getJoinedCount() >= context.activity().getTotalStock()) {
            return new GroupEligibility(false, false, "活动名额已满");
        }
        return new GroupEligibility(true, false, "可以参加");
    }
}
