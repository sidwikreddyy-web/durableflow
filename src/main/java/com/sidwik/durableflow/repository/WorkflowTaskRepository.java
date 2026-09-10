package com.sidwik.durableflow.repository;

import com.sidwik.durableflow.domain.TaskStatus;
import com.sidwik.durableflow.domain.WorkflowTask;
import com.sidwik.durableflow.domain.WorkflowTaskType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTaskRepository {
    void enqueue(WorkflowTask task);
    Optional<WorkflowTask> claimNext(String workerId, int leaseSeconds);
    boolean complete(UUID taskId, String workerId, Instant now);
    void reschedule(UUID taskId, String workerId, Instant availableAt, String error, Instant now);
    void fail(UUID taskId, String workerId, String error, Instant now);
    void cancel(UUID taskId, Instant now);
    void cancelPendingForWorkflow(UUID workflowId, Instant now);
    int releaseExpiredLeases(Instant now);
}

@Repository
class JdbcWorkflowTaskRepository implements WorkflowTaskRepository {
    private final JdbcClient jdbcClient;

    JdbcWorkflowTaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void enqueue(WorkflowTask task) {
        jdbcClient.sql("""
                INSERT INTO workflow_tasks
                    (id, workflow_id, task_type, status, attempt, max_attempts, available_at,
                     lease_owner, lease_expires_at, idempotency_key, last_error, created_at, updated_at)
                VALUES
                    (:id, :workflowId, :taskType, :status, :attempt, :maxAttempts, :availableAt,
                     :leaseOwner, :leaseExpiresAt, :idempotencyKey, :lastError, :createdAt, :updatedAt)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id", task.id())
                .param("workflowId", task.workflowId())
                .param("taskType", task.type().name())
                .param("status", task.status().name())
                .param("attempt", task.attempt())
                .param("maxAttempts", task.maxAttempts())
                .param("availableAt", java.sql.Timestamp.from(task.availableAt()))
                .param("leaseOwner", task.leaseOwner())
                .param("leaseExpiresAt", timestampOrNull(task.leaseExpiresAt()))
                .param("idempotencyKey", task.idempotencyKey())
                .param("lastError", task.lastError())
                .param("createdAt", java.sql.Timestamp.from(task.createdAt()))
                .param("updatedAt", java.sql.Timestamp.from(task.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public Optional<WorkflowTask> claimNext(String workerId, int leaseSeconds) {
        return jdbcClient.sql("""
                WITH candidate AS (
                    SELECT id
                    FROM workflow_tasks
                    WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE workflow_tasks task
                SET status = 'RUNNING',
                    attempt = task.attempt + 1,
                    lease_owner = :workerId,
                    lease_expires_at = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE task.id = candidate.id
                RETURNING task.*
                """)
                .param("workerId", workerId)
                .param("leaseSeconds", leaseSeconds)
                .query(JdbcWorkflowTaskRepository::map)
                .optional();
    }

    @Override
    public boolean complete(UUID taskId, String workerId, Instant now) {
        return updateTerminal(taskId, workerId, TaskStatus.COMPLETED, null, now) == 1;
    }

    @Override
    public void reschedule(UUID taskId, String workerId, Instant availableAt, String error, Instant now) {
        jdbcClient.sql("""
                UPDATE workflow_tasks
                SET status = 'PENDING', available_at = :availableAt, last_error = :error,
                    lease_owner = NULL, lease_expires_at = NULL, updated_at = :now
                WHERE id = :taskId AND status = 'RUNNING' AND lease_owner = :workerId
                """)
                .param("availableAt", java.sql.Timestamp.from(availableAt))
                .param("error", error)
                .param("now", java.sql.Timestamp.from(now))
                .param("taskId", taskId)
                .param("workerId", workerId)
                .update();
    }

    @Override
    public void fail(UUID taskId, String workerId, String error, Instant now) {
        updateTerminal(taskId, workerId, TaskStatus.FAILED, error, now);
    }

    private int updateTerminal(UUID taskId, String workerId, TaskStatus status, String error, Instant now) {
        return jdbcClient.sql("""
                UPDATE workflow_tasks
                SET status = :status, last_error = :error, lease_owner = NULL,
                    lease_expires_at = NULL, updated_at = :now
                WHERE id = :taskId AND status = 'RUNNING' AND lease_owner = :workerId
                """)
                .param("status", status.name())
                .param("error", error)
                .param("now", java.sql.Timestamp.from(now))
                .param("taskId", taskId)
                .param("workerId", workerId)
                .update();
    }

    @Override
    public void cancel(UUID taskId, Instant now) {
        jdbcClient.sql("""
                UPDATE workflow_tasks SET status = 'CANCELLED', updated_at = :now,
                    lease_owner = NULL, lease_expires_at = NULL
                WHERE id = :taskId AND status IN ('PENDING', 'RUNNING')
                """)
                .param("now", java.sql.Timestamp.from(now)).param("taskId", taskId).update();
    }

    @Override
    public void cancelPendingForWorkflow(UUID workflowId, Instant now) {
        jdbcClient.sql("""
                UPDATE workflow_tasks SET status = 'CANCELLED', updated_at = :now,
                    lease_owner = NULL, lease_expires_at = NULL
                WHERE workflow_id = :workflowId AND status IN ('PENDING', 'RUNNING')
                """)
                .param("now", java.sql.Timestamp.from(now)).param("workflowId", workflowId).update();
    }

    @Override
    public int releaseExpiredLeases(Instant now) {
        return jdbcClient.sql("""
                UPDATE workflow_tasks
                SET status = 'PENDING', lease_owner = NULL, lease_expires_at = NULL,
                    available_at = :now, updated_at = :now
                WHERE status = 'RUNNING' AND lease_expires_at < :now
                """)
                .param("now", java.sql.Timestamp.from(now))
                .update();
    }

    private static WorkflowTask map(ResultSet rs, int rowNum) throws SQLException {
        var leaseExpiry = rs.getTimestamp("lease_expires_at");
        return new WorkflowTask(
                rs.getObject("id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                WorkflowTaskType.valueOf(rs.getString("task_type")),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt"),
                rs.getInt("max_attempts"),
                rs.getTimestamp("available_at").toInstant(),
                rs.getString("lease_owner"),
                leaseExpiry == null ? null : leaseExpiry.toInstant(),
                rs.getString("idempotency_key"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static java.sql.Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : java.sql.Timestamp.from(instant);
    }
}
