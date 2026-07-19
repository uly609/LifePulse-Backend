package com.lifepulse.user;

public record LoginResponse(Long userId, String username, String role, String token) {
}
