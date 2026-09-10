package com.sidwik.durableflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowEvent(
        long id,
        UUID workflowId,
        String eventType,
        String details,
        Instant createdAt
) {
}
