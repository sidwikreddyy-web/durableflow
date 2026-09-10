package com.sidwik.durableflow.service;

import com.sidwik.durableflow.domain.Workflow;
import com.sidwik.durableflow.domain.WorkflowEvent;
import com.sidwik.durableflow.domain.WorkflowTask;
import com.sidwik.durableflow.domain.WorkflowTaskType;
import com.sidwik.durableflow.exception.ConflictException;
import com.sidwik.durableflow.exception.NotFoundException;
import com.sidwik.durableflow.repository.WorkflowEventRepository;
import com.sidwik.durableflow.repository.WorkflowRepository;
import com.sidwik.durableflow.repository.WorkflowTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowRepository workflows;
    private final WorkflowEventRepository events;
    private final WorkflowTaskRepository tasks;
    private final Clock clock;

    public WorkflowService(
            WorkflowRepository workflows,
            WorkflowEventRepository events,
            WorkflowTaskRepository tasks,
            Clock clock
    ) {
        this.workflows = workflows;
        this.events = events;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional
    public Workflow start(UUID ownerId, String name) {
        Instant now = clock.instant();
        Workflow workflow = Workflow.start(UUID.randomUUID(), ownerId, name, now);
        workflows.insert(workflow);
        events.append(workflow.getId(), "WORKFLOW_STARTED", "Order fulfilment workflow started", now);
        tasks.enqueue(taskFor(workflow.getId(), WorkflowTaskType.RESERVE_INVENTORY, now));
        return workflow;
    }

    @Transactional(readOnly = true)
    public Workflow get(UUID ownerId, UUID workflowId) {
        return workflows.findByIdAndOwner(workflowId, ownerId)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
    }

    @Transactional(readOnly = true)
    public List<Workflow> list(UUID ownerId) {
        return workflows.findByOwner(ownerId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowEvent> events(UUID ownerId, UUID workflowId) {
        get(ownerId, workflowId);
        return events.findByWorkflow(workflowId);
    }

    @Transactional
    public Workflow cancel(UUID ownerId, UUID workflowId) {
        Workflow workflow = get(ownerId, workflowId);
        long expectedVersion = workflow.getVersion();
        try {
            workflow.cancel(clock.instant());
        } catch (IllegalStateException exception) {
            throw new ConflictException(exception.getMessage());
        }
        if (!workflows.update(workflow, expectedVersion)) {
            throw new ConflictException("Workflow was updated concurrently; retry the request");
        }
        tasks.cancelPendingForWorkflow(workflowId, workflow.getUpdatedAt());
        events.append(workflowId, "WORKFLOW_CANCELLED", "Cancelled by user", workflow.getUpdatedAt());
        return workflow;
    }

    static WorkflowTask taskFor(UUID workflowId, WorkflowTaskType type, Instant now) {
        return WorkflowTask.pending(workflowId, type, workflowId + ":" + type.name(), now);
    }
}
