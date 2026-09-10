package com.sidwik.durableflow.service;

import com.sidwik.durableflow.domain.*;
import com.sidwik.durableflow.exception.ConflictException;
import com.sidwik.durableflow.exception.NotFoundException;
import com.sidwik.durableflow.repository.WorkflowEventRepository;
import com.sidwik.durableflow.repository.WorkflowRepository;
import com.sidwik.durableflow.repository.WorkflowTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class WorkflowEngineService {
    private final WorkflowRepository workflows;
    private final WorkflowEventRepository events;
    private final WorkflowTaskRepository tasks;
    private final Clock clock;

    public WorkflowEngineService(WorkflowRepository workflows, WorkflowEventRepository events,
                                 WorkflowTaskRepository tasks, Clock clock) {
        this.workflows = workflows;
        this.events = events;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional
    public void activitySucceeded(WorkflowTask task, String workerId) {
        Instant now = clock.instant();
        Workflow workflow = load(task);
        if (workflow.getStatus() == WorkflowStatus.CANCELLED) {
            tasks.cancel(task.id(), now);
            return;
        }

        long expectedVersion = workflow.getVersion();
        WorkflowTaskType nextTask = switch (task.type()) {
            case RESERVE_INVENTORY -> {
                workflow.advanceTo(WorkflowStep.CHARGE_PAYMENT, now);
                yield WorkflowTaskType.CHARGE_PAYMENT;
            }
            case CHARGE_PAYMENT -> {
                workflow.advanceTo(WorkflowStep.CREATE_SHIPMENT, now);
                yield WorkflowTaskType.CREATE_SHIPMENT;
            }
            case CREATE_SHIPMENT -> {
                workflow.complete(now);
                yield null;
            }
            case REFUND_PAYMENT -> {
                workflow.advanceCompensationTo(WorkflowStep.RELEASE_INVENTORY, now);
                yield WorkflowTaskType.RELEASE_INVENTORY;
            }
            case RELEASE_INVENTORY -> {
                workflow.markCompensated(now);
                yield null;
            }
        };

        persistTransition(workflow, expectedVersion);
        if (!tasks.complete(task.id(), workerId, now)) {
            throw new ConflictException("Task lease expired before completion");
        }
        events.append(workflow.getId(), "ACTIVITY_COMPLETED", task.type().name(), now);
        if (workflow.getStatus() == WorkflowStatus.COMPLETED) {
            events.append(workflow.getId(), "WORKFLOW_COMPLETED", "All activities completed", now);
        } else if (workflow.getStatus() == WorkflowStatus.COMPENSATED) {
            events.append(workflow.getId(), "WORKFLOW_COMPENSATED", workflow.getFailureReason(), now);
        }
        schedule(workflow, nextTask, now);
    }

    @Transactional
    public void activityFailed(WorkflowTask task, String workerId, String error) {
        Instant now = clock.instant();
        if (task.attempt() < task.maxAttempts()) {
            long delaySeconds = Math.min(30, 1L << task.attempt());
            tasks.reschedule(task.id(), workerId, now.plus(Duration.ofSeconds(delaySeconds)), error, now);
            events.append(task.workflowId(), "ACTIVITY_RETRY_SCHEDULED",
                    task.type() + " attempt " + task.attempt() + " failed: " + error, now);
            return;
        }

        Workflow workflow = load(task);
        if (workflow.getStatus() == WorkflowStatus.CANCELLED) {
            tasks.cancel(task.id(), now);
            return;
        }

        long expectedVersion = workflow.getVersion();
        WorkflowTaskType compensation = switch (task.type()) {
            case RESERVE_INVENTORY -> {
                workflow.fail(error, now);
                yield null;
            }
            case CHARGE_PAYMENT -> {
                workflow.beginCompensation(WorkflowStep.RELEASE_INVENTORY, error, now);
                yield WorkflowTaskType.RELEASE_INVENTORY;
            }
            case CREATE_SHIPMENT -> {
                workflow.beginCompensation(WorkflowStep.REFUND_PAYMENT, error, now);
                yield WorkflowTaskType.REFUND_PAYMENT;
            }
            case REFUND_PAYMENT, RELEASE_INVENTORY -> {
                workflow.fail("Compensation failed: " + error, now);
                yield null;
            }
        };

        persistTransition(workflow, expectedVersion);
        tasks.fail(task.id(), workerId, error, now);
        events.append(workflow.getId(), "ACTIVITY_FAILED", task.type() + ": " + error, now);
        if (workflow.getStatus() == WorkflowStatus.FAILED) {
            events.append(workflow.getId(), "WORKFLOW_FAILED", workflow.getFailureReason(), now);
        } else {
            events.append(workflow.getId(), "COMPENSATION_STARTED", compensation.name(), now);
        }
        schedule(workflow, compensation, now);
    }

    private Workflow load(WorkflowTask task) {
        return workflows.findById(task.workflowId())
                .orElseThrow(() -> new NotFoundException("Workflow for task does not exist"));
    }

    private void persistTransition(Workflow workflow, long expectedVersion) {
        if (!workflows.update(workflow, expectedVersion)) {
            throw new ConflictException("Workflow was advanced by another worker");
        }
    }

    private void schedule(Workflow workflow, WorkflowTaskType nextTask, Instant now) {
        if (nextTask != null) {
            tasks.enqueue(WorkflowService.taskFor(workflow.getId(), nextTask, now));
            events.append(workflow.getId(), "ACTIVITY_SCHEDULED", nextTask.name(), now);
        }
    }
}
