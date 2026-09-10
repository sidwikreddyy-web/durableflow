package com.sidwik.durableflow.service;

import com.sidwik.durableflow.domain.Workflow;
import com.sidwik.durableflow.domain.WorkflowTask;
import com.sidwik.durableflow.exception.NotFoundException;
import com.sidwik.durableflow.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WorkflowTaskProcessor {
    private final WorkflowRepository workflows;
    private final WorkflowEngineService engine;

    public WorkflowTaskProcessor(WorkflowRepository workflows, WorkflowEngineService engine) {
        this.workflows = workflows;
        this.engine = engine;
    }

    public void process(WorkflowTask task, String workerId) {
        Workflow workflow = workflows.findById(task.workflowId())
                .orElseThrow(() -> new NotFoundException("Workflow for task does not exist"));
        try {
            executeDemoActivity(workflow, task);
        } catch (ActivityException exception) {
            engine.activityFailed(task, workerId, safeMessage(exception));
            return;
        }
        engine.activitySucceeded(task, workerId);
    }

    private void executeDemoActivity(Workflow workflow, WorkflowTask task) {
        String name = workflow.getName().toLowerCase(Locale.ROOT);
        switch (task.type()) {
            case RESERVE_INVENTORY -> {
                if (name.contains("fail-inventory")) throw new ActivityException("Inventory unavailable");
            }
            case CHARGE_PAYMENT -> {
                if (name.contains("retry-payment") && task.attempt() == 1) {
                    throw new ActivityException("Payment gateway temporarily unavailable");
                }
            }
            case CREATE_SHIPMENT -> {
                if (name.contains("fail-shipping")) throw new ActivityException("Shipping provider rejected order");
            }
            case REFUND_PAYMENT, RELEASE_INVENTORY -> { }
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class ActivityException extends RuntimeException {
        private ActivityException(String message) { super(message); }
    }
}
