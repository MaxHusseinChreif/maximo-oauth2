package com.ess.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public final class JwtTokenService {

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final long expirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtTokenService(
            JwtKeyProvider keyProvider,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.key-id}") String keyId,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this(keyProvider, issuer, audience, keyId, expirationSeconds, Clock.systemUTC());
    }

    JwtTokenService(
            JwtKeyProvider keyProvider,
            String issuer,
            String audience,
            String keyId,
            long expirationSeconds,
            Clock clock) {
        this.keyProvider = java.util.Objects.requireNonNull(keyProvider, "keyProvider");
        this.issuer = requireText("JWT_ISSUER", issuer);
        this.audience = requireText("JWT_AUDIENCE", audience);
        this.keyId = requireText("JWT_KEY_ID", keyId);
        if (expirationSeconds <= 0 || expirationSeconds > 3600) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be between 1 and 3600");
        }
        this.expirationSeconds = expirationSeconds;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public String issueToken(String subject, Map<String, Object> additionalClaims) {
        String validatedSubject = requireText("JWT subject", subject);
        Instant issuedAt = clock.instant();

        return Jwts.builder()
                .setHeaderParam("kid", keyId)
                .setClaims(new HashMap<>(additionalClaims))
                .setSubject(validatedSubject)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(SignatureAlgorithm.RS256, keyProvider.privateKey())
                .compact();
    }

    public Jws<Claims> parseAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        String compactToken = authorizationHeader.substring(7).trim();
        if (compactToken.isEmpty()) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        return Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience)
                .setClock(() -> Date.from(clock.instant()))
                .setSigningKey(keyProvider.publicKey())
                .parseClaimsJws(compactToken);
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private static String requireText(String settingName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " must be configured");
        }
        return value;
    }
}
