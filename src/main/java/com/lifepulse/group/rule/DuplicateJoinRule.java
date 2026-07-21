package com.lifepulse.group.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifepulse.entity.GroupMember;
import com.lifepulse.group.GroupEligibility;
import com.lifepulse.mapper.GroupMemberMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DuplicateJoinRule implements GroupJoinRule {
    private final GroupMemberMapper memberMapper;

    public DuplicateJoinRule(GroupMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Override
    public GroupEligibility evaluate(GroupJoinContext context) {
        boolean joined = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getActivityId, context.activity().getId())
                .eq(GroupMember::getUserId, context.userId())) > 0;
        if (joined) {
            return new GroupEligibility(false, true, "你已参加该活动");
        }
        return null;
    }
}
