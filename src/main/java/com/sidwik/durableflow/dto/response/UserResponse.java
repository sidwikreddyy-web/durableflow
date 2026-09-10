package com.sidwik.durableflow.dto.response;

import com.sidwik.durableflow.domain.AppUser;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String role, Instant createdAt) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.id(), user.email(), user.role().name(), user.createdAt());
    }
}
