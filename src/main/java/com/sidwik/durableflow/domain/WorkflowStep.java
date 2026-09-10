package com.sidwik.durableflow.domain;

public enum WorkflowStep {
    RESERVE_INVENTORY,
    CHARGE_PAYMENT,
    CREATE_SHIPMENT,
    REFUND_PAYMENT,
    RELEASE_INVENTORY,
    NONE
}
