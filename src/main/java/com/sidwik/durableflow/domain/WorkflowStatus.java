package com.sidwik.durableflow.domain;

public enum WorkflowStatus {
    RUNNING,
    COMPENSATING,
    COMPENSATED,
    COMPLETED,
    FAILED,
    CANCELLED
}
