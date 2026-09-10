package com.sidwik.durableflow.domain;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        Instant createdAt
) {
}
