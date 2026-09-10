package com.sidwik.durableflow.security;

import com.sidwik.durableflow.domain.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            JwtEncoder jwtEncoder,
            @Value("${durableflow.auth.issuer}") String issuer,
            @Value("${durableflow.auth.access-token-ttl}") Duration accessTokenTtl,
            @Value("${durableflow.auth.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String createAccessToken(AppUser user, Instant now) {
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .claim("email", user.email())
                .claim("roles", List.of(user.role().name()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String createRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }
}
