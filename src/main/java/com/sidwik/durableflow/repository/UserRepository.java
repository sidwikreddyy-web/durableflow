package com.sidwik.durableflow.repository;

import com.sidwik.durableflow.domain.AppUser;
import com.sidwik.durableflow.domain.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void save(AppUser user);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findById(UUID id);
    boolean existsByEmail(String email);
}

@Repository
class JdbcUserRepository implements UserRepository {
    private final JdbcClient jdbcClient;

    JdbcUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(AppUser user) {
        jdbcClient.sql("""
                INSERT INTO app_users (id, email, password_hash, role, created_at)
                VALUES (:id, :email, :passwordHash, :role, :createdAt)
                """)
                .param("id", user.id())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("role", user.role().name())
                .param("createdAt", java.sql.Timestamp.from(user.createdAt()))
                .update();
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return jdbcClient.sql("SELECT * FROM app_users WHERE email = :email")
                .param("email", email)
                .query(JdbcUserRepository::map)
                .optional();
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM app_users WHERE id = :id")
                .param("id", id)
                .query(JdbcUserRepository::map)
                .optional();
    }

    @Override
    public boolean existsByEmail(String email) {
        return Boolean.TRUE.equals(jdbcClient.sql("SELECT EXISTS(SELECT 1 FROM app_users WHERE email = :email)")
                .param("email", email)
                .query(Boolean.class)
                .single());
    }

    private static AppUser map(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
