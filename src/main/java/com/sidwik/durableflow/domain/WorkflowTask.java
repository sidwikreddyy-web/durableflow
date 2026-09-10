package com.sidwik.durableflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTask(
        UUID id,
        UUID workflowId,
        WorkflowTaskType type,
        TaskStatus status,
        int attempt,
        int maxAttempts,
        Instant availableAt,
        String leaseOwner,
        Instant leaseExpiresAt,
        String idempotencyKey,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowTask pending(
            UUID workflowId,
            WorkflowTaskType type,
            String idempotencyKey,
            Instant now
    ) {
        return new WorkflowTask(UUID.randomUUID(), workflowId, type, TaskStatus.PENDING,
                0, 3, now, null, null, idempotencyKey, null, now, now);
    }
}
