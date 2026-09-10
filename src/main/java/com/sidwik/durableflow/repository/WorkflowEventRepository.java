package com.sidwik.durableflow.repository;

import com.sidwik.durableflow.domain.WorkflowEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkflowEventRepository {
    void append(UUID workflowId, String eventType, String details, Instant now);
    List<WorkflowEvent> findByWorkflow(UUID workflowId);
}

@Repository
class JdbcWorkflowEventRepository implements WorkflowEventRepository {
    private final JdbcClient jdbcClient;

    JdbcWorkflowEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(UUID workflowId, String eventType, String details, Instant now) {
        jdbcClient.sql("""
                INSERT INTO workflow_events (workflow_id, event_type, details, created_at)
                VALUES (:workflowId, :eventType, :details, :createdAt)
                """)
                .param("workflowId", workflowId)
                .param("eventType", eventType)
                .param("details", details)
                .param("createdAt", java.sql.Timestamp.from(now))
                .update();
    }

    @Override
    public List<WorkflowEvent> findByWorkflow(UUID workflowId) {
        return jdbcClient.sql("""
                SELECT id, workflow_id, event_type, details, created_at
                FROM workflow_events
                WHERE workflow_id = :workflowId
                ORDER BY id
                """)
                .param("workflowId", workflowId)
                .query(JdbcWorkflowEventRepository::map)
                .list();
    }

    private static WorkflowEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowEvent(
                rs.getLong("id"),
                rs.getObject("workflow_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("details"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
