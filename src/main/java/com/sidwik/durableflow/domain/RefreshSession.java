package com.sidwik.durableflow.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBy,
        Instant createdAt
) {
}
