package com.lifepulse.auth;

public record JwtClaims(Long userId, String username, String role) {
}
