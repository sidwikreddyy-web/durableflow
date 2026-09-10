package com.sidwik.durableflow.dto.response;

import com.sidwik.durableflow.domain.Workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String name,
        String status,
        String currentStep,
        long version,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowResponse from(Workflow workflow) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getStatus().name(),
                workflow.getCurrentStep().name(),
                workflow.getVersion(),
                workflow.getFailureReason(),
                workflow.getCreatedAt(),
                workflow.getUpdatedAt()
        );
    }
}
