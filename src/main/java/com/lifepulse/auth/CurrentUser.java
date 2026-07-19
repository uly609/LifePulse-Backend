package com.lifepulse.auth;

import com.lifepulse.common.BusinessException;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long resolve(Long fallbackUserId) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            return userId;
        }
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        throw new BusinessException("请先登录");
    }
}
