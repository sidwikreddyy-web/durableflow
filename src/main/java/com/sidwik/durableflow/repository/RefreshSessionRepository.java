package com.sidwik.durableflow.repository;

import com.sidwik.durableflow.domain.RefreshSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {
    void save(RefreshSession session);
    Optional<RefreshSession> findActive(String tokenHash, Instant now);
    void revoke(UUID id, UUID replacedBy, Instant now);
    void revokeByHash(String tokenHash, Instant now);
}

@Repository
class JdbcRefreshSessionRepository implements RefreshSessionRepository {
    private final JdbcClient jdbcClient;

    JdbcRefreshSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(RefreshSession session) {
        jdbcClient.sql("""
                INSERT INTO refresh_sessions
                    (id, user_id, token_hash, expires_at, revoked_at, replaced_by, created_at)
                VALUES
                    (:id, :userId, :tokenHash, :expiresAt, :revokedAt, :replacedBy, :createdAt)
                """)
                .param("id", session.id())
                .param("userId", session.userId())
                .param("tokenHash", session.tokenHash())
                .param("expiresAt", java.sql.Timestamp.from(session.expiresAt()))
                .param("revokedAt", timestampOrNull(session.revokedAt()))
                .param("replacedBy", session.replacedBy())
                .param("createdAt", java.sql.Timestamp.from(session.createdAt()))
                .update();
    }

    @Override
    public Optional<RefreshSession> findActive(String tokenHash, Instant now) {
        return jdbcClient.sql("""
                SELECT * FROM refresh_sessions
                WHERE token_hash = :tokenHash
                  AND revoked_at IS NULL
                  AND expires_at > :now
                """)
                .param("tokenHash", tokenHash)
                .param("now", java.sql.Timestamp.from(now))
                .query(JdbcRefreshSessionRepository::map)
                .optional();
    }

    @Override
    public void revoke(UUID id, UUID replacedBy, Instant now) {
        jdbcClient.sql("""
                UPDATE refresh_sessions
                SET revoked_at = :now, replaced_by = :replacedBy
                WHERE id = :id AND revoked_at IS NULL
                """)
                .param("now", java.sql.Timestamp.from(now))
                .param("replacedBy", replacedBy)
                .param("id", id)
                .update();
    }

    @Override
    public void revokeByHash(String tokenHash, Instant now) {
        jdbcClient.sql("""
                UPDATE refresh_sessions SET revoked_at = :now
                WHERE token_hash = :tokenHash AND revoked_at IS NULL
                """)
                .param("now", java.sql.Timestamp.from(now))
                .param("tokenHash", tokenHash)
                .update();
    }

    private static RefreshSession map(ResultSet rs, int rowNum) throws SQLException {
        var revoked = rs.getTimestamp("revoked_at");
        return new RefreshSession(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                revoked == null ? null : revoked.toInstant(),
                rs.getObject("replaced_by", UUID.class),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static java.sql.Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : java.sql.Timestamp.from(instant);
    }
}
