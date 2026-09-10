package com.sidwik.durableflow.dto.response;

import com.sidwik.durableflow.domain.WorkflowEvent;

import java.time.Instant;

public record WorkflowEventResponse(
        long id,
        String eventType,
        String details,
        Instant createdAt
) {
    public static WorkflowEventResponse from(WorkflowEvent event) {
        return new WorkflowEventResponse(
                event.id(), event.eventType(), event.details(), event.createdAt());
    }
}
