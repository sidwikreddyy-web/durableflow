package com.sidwik.durableflow.service;

import com.sidwik.durableflow.domain.AppUser;
import com.sidwik.durableflow.domain.RefreshSession;
import com.sidwik.durableflow.domain.Role;
import com.sidwik.durableflow.exception.ConflictException;
import com.sidwik.durableflow.exception.NotFoundException;
import com.sidwik.durableflow.exception.UnauthorizedException;
import com.sidwik.durableflow.repository.RefreshSessionRepository;
import com.sidwik.durableflow.repository.UserRepository;
import com.sidwik.durableflow.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            RefreshSessionRepository sessions,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            Clock clock
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public AuthSession register(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Instant now = clock.instant();
        var user = new AppUser(UUID.randomUUID(), email, passwordEncoder.encode(password), Role.USER, now);
        users.save(user);
        return issueSession(user, now);
    }

    @Transactional
    public AuthSession login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueSession(user, clock.instant());
    }

    @Transactional
    public AuthSession refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshSession current = sessions.findActive(tokens.hashRefreshToken(refreshToken), now)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
        AppUser user = users.findById(current.userId())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));

        String replacementToken = tokens.createRefreshToken();
        UUID replacementId = UUID.randomUUID();
        sessions.save(new RefreshSession(
                replacementId,
                user.id(),
                tokens.hashRefreshToken(replacementToken),
                now.plus(tokens.refreshTokenTtl()),
                null,
                null,
                now
        ));
        sessions.revoke(current.id(), replacementId, now);
        return new AuthSession(tokens.createAccessToken(user, now), replacementToken,
                tokens.accessTokenTtl().toSeconds(), user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.revokeByHash(tokens.hashRefreshToken(refreshToken), clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public AppUser getUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthSession issueSession(AppUser user, Instant now) {
        String refreshToken = tokens.createRefreshToken();
        sessions.save(new RefreshSession(
                UUID.randomUUID(),
                user.id(),
                tokens.hashRefreshToken(refreshToken),
                now.plus(tokens.refreshTokenTtl()),
                null,
                null,
                now
        ));
        return new AuthSession(tokens.createAccessToken(user, now), refreshToken,
                tokens.accessTokenTtl().toSeconds(), user);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public record AuthSession(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            AppUser user
    ) {
    }
}
