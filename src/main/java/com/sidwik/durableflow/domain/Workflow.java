package com.sidwik.durableflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Workflow {

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private WorkflowStatus status;
    private long version;
    private WorkflowStep currentStep;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Workflow(
            UUID id,
            UUID ownerId,
            String name,
            WorkflowStatus status,
            long version,
            WorkflowStep currentStep,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.status = status;
        this.version = version;
        this.currentStep = currentStep;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Workflow start(UUID id, UUID ownerId, String name, Instant createdAt) {
        Objects.requireNonNull(id, "Workflow id must not be null");
        Objects.requireNonNull(ownerId, "Workflow owner id must not be null");
        Objects.requireNonNull(createdAt, "Workflow creation time must not be null");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workflow name must not be blank");
        }

        return new Workflow(
                id,
                ownerId,
                name.trim(),
                WorkflowStatus.RUNNING,
                0,
                WorkflowStep.RESERVE_INVENTORY,
                null,
                createdAt,
                createdAt
        );
    }

    public static Workflow rehydrate(
            UUID id,
            UUID ownerId,
            String name,
            WorkflowStatus status,
            long version,
            WorkflowStep currentStep,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Workflow(id, ownerId, name, status, version, currentStep,
                failureReason, createdAt, updatedAt);
    }

    public void cancel(Instant now) {
        if (status != WorkflowStatus.RUNNING) {
            throw new IllegalStateException("Only running workflows can be cancelled");
        }

        status = WorkflowStatus.CANCELLED;
        currentStep = WorkflowStep.NONE;
        touch(now);
    }

    public void advanceTo(WorkflowStep nextStep, Instant now) {
        requireStatus(WorkflowStatus.RUNNING);
        currentStep = Objects.requireNonNull(nextStep);
        touch(now);
    }

    public void complete(Instant now) {
        requireStatus(WorkflowStatus.RUNNING);
        status = WorkflowStatus.COMPLETED;
        currentStep = WorkflowStep.NONE;
        touch(now);
    }

    public void beginCompensation(WorkflowStep firstStep, String reason, Instant now) {
        requireStatus(WorkflowStatus.RUNNING);
        status = WorkflowStatus.COMPENSATING;
        currentStep = firstStep;
        failureReason = reason;
        touch(now);
    }

    public void advanceCompensationTo(WorkflowStep nextStep, Instant now) {
        requireStatus(WorkflowStatus.COMPENSATING);
        currentStep = nextStep;
        touch(now);
    }

    public void markCompensated(Instant now) {
        requireStatus(WorkflowStatus.COMPENSATING);
        status = WorkflowStatus.COMPENSATED;
        currentStep = WorkflowStep.NONE;
        touch(now);
    }

    public void fail(String reason, Instant now) {
        if (status != WorkflowStatus.RUNNING && status != WorkflowStatus.COMPENSATING) {
            throw new IllegalStateException("Only active workflows can fail");
        }
        status = WorkflowStatus.FAILED;
        currentStep = WorkflowStep.NONE;
        failureReason = reason;
        touch(now);
    }

    private void requireStatus(WorkflowStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected workflow status " + expected + " but was " + status);
        }
    }

    private void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now);
        version++;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public WorkflowStep getCurrentStep() {
        return currentStep;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
