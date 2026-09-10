package com.sidwik.durableflow.repository;

import com.sidwik.durableflow.domain.Workflow;
import com.sidwik.durableflow.domain.WorkflowStatus;
import com.sidwik.durableflow.domain.WorkflowStep;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository {
    void insert(Workflow workflow);
    boolean update(Workflow workflow, long expectedVersion);
    Optional<Workflow> findById(UUID id);
    Optional<Workflow> findByIdAndOwner(UUID id, UUID ownerId);
    List<Workflow> findByOwner(UUID ownerId);
}

@Repository
class JdbcWorkflowRepository implements WorkflowRepository {
    private final JdbcClient jdbcClient;

    JdbcWorkflowRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(Workflow workflow) {
        jdbcClient.sql("""
                INSERT INTO workflow_instances
                    (id, owner_id, name, status, version, current_step, failure_reason, created_at, updated_at)
                VALUES
                    (:id, :ownerId, :name, :status, :version, :currentStep, :failureReason, :createdAt, :updatedAt)
                """)
                .param("id", workflow.getId())
                .param("ownerId", workflow.getOwnerId())
                .param("name", workflow.getName())
                .param("status", workflow.getStatus().name())
                .param("version", workflow.getVersion())
                .param("currentStep", workflow.getCurrentStep().name())
                .param("failureReason", workflow.getFailureReason())
                .param("createdAt", java.sql.Timestamp.from(workflow.getCreatedAt()))
                .param("updatedAt", java.sql.Timestamp.from(workflow.getUpdatedAt()))
                .update();
    }

    @Override
    public boolean update(Workflow workflow, long expectedVersion) {
        return jdbcClient.sql("""
                UPDATE workflow_instances
                SET status = :status,
                    version = :version,
                    current_step = :currentStep,
                    failure_reason = :failureReason,
                    updated_at = :updatedAt
                WHERE id = :id AND version = :expectedVersion
                """)
                .param("status", workflow.getStatus().name())
                .param("version", workflow.getVersion())
                .param("currentStep", workflow.getCurrentStep().name())
                .param("failureReason", workflow.getFailureReason())
                .param("updatedAt", java.sql.Timestamp.from(workflow.getUpdatedAt()))
                .param("id", workflow.getId())
                .param("expectedVersion", expectedVersion)
                .update() == 1;
    }

    @Override
    public Optional<Workflow> findById(UUID id) {
        return query("SELECT * FROM workflow_instances WHERE id = :id", id, null);
    }

    @Override
    public Optional<Workflow> findByIdAndOwner(UUID id, UUID ownerId) {
        return query("SELECT * FROM workflow_instances WHERE id = :id AND owner_id = :ownerId", id, ownerId);
    }

    private Optional<Workflow> query(String sql, UUID id, UUID ownerId) {
        var statement = jdbcClient.sql(sql).param("id", id);
        if (ownerId != null) {
            statement = statement.param("ownerId", ownerId);
        }
        return statement.query(JdbcWorkflowRepository::map).optional();
    }

    @Override
    public List<Workflow> findByOwner(UUID ownerId) {
        return jdbcClient.sql("""
                SELECT * FROM workflow_instances
                WHERE owner_id = :ownerId
                ORDER BY created_at DESC
                LIMIT 100
                """)
                .param("ownerId", ownerId)
                .query(JdbcWorkflowRepository::map)
                .list();
    }

    private static Workflow map(ResultSet rs, int rowNum) throws SQLException {
        return Workflow.rehydrate(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("name"),
                WorkflowStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                WorkflowStep.valueOf(rs.getString("current_step")),
                rs.getString("failure_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
