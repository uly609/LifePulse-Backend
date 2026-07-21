package com.lifepulse.group.rule;

import com.lifepulse.entity.GroupActivity;

public record GroupJoinContext(GroupActivity activity, Long userId, String role) {
}
