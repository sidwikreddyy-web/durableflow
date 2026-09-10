package com.sidwik.durableflow.controller;

import com.sidwik.durableflow.dto.request.LoginRequest;
import com.sidwik.durableflow.dto.request.RegisterRequest;
import com.sidwik.durableflow.dto.response.AuthResponse;
import com.sidwik.durableflow.dto.response.UserResponse;
import com.sidwik.durableflow.exception.UnauthorizedException;
import com.sidwik.durableflow.security.TokenService;
import com.sidwik.durableflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "durableflow_refresh";
    private final AuthService authService;
    private final TokenService tokenService;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public AuthController(AuthService authService, TokenService tokenService,
                          @Value("${durableflow.auth.secure-cookies:false}") boolean secureCookies,
                          @Value("${durableflow.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.secureCookies = secureCookies;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return response(authService.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return response(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) throw new UnauthorizedException("Refresh token cookie is missing");
        return response(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.getUser(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<AuthResponse> response(AuthService.AuthSession session) {
        var body = new AuthResponse(session.accessToken(), "Bearer", session.expiresInSeconds(),
                UserResponse.from(session.user()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/auth")
                .maxAge(tokenService.refreshTokenTtl()).build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/auth")
                .maxAge(0).build();
    }
}
