package com.lifepulse.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            JwtClaims claims = jwtService.parseClaims(authorization.substring(7));
            UserContext.setClaims(claims);
            MDC.put("userId", String.valueOf(claims.userId()));
            return true;
        }
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            JwtClaims claims = jwtService.parseClaims(token);
            UserContext.setClaims(claims);
            MDC.put("userId", String.valueOf(claims.userId()));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
        MDC.remove("userId");
    }
}
