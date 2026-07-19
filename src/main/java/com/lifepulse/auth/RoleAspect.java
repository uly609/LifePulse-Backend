package com.lifepulse.auth;

import com.lifepulse.common.BusinessException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class RoleAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        String currentRole = UserContext.getRole();
        if (currentRole == null) {
            throw new BusinessException("请先登录");
        }
        boolean allowed = Arrays.asList(requireRole.value()).contains(currentRole);
        if (!allowed) {
            throw new BusinessException("无权限访问该功能");
        }
    }
}
