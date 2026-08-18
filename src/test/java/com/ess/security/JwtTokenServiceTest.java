package com.ess.security;

import com.ess.support.JwtKeyTestFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");

    @Test
    void issuesTokenWithRequiredSecurityClaimsAndKeyId() throws Exception {
        JwtTokenService service = service("maximo-api");

        String token = service.issueToken("test-client", Map.of("username", "test-client"));
        Jws<Claims> parsed = service.parseAuthorizationHeader("Bearer " + token);

        assertEquals("maximo-oauth2", parsed.getBody().getIssuer());
        assertEquals("maximo-api", parsed.getBody().getAudience());
        assertEquals("test-client", parsed.getBody().getSubject());
        assertEquals("primary-test-key", parsed.getHeader().getKeyId());
        assertEquals(1800, parsed.getBody().getExpiration().toInstant().getEpochSecond()
                - parsed.getBody().getIssuedAt().toInstant().getEpochSecond());
    }

    @Test
    void rejectsTokenIssuedForDifferentAudience() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenService issuer = new JwtTokenService(
                keys, "maximo-oauth2", "different-api", "primary-test-key", 1800, clock);
        JwtTokenService validator = new JwtTokenService(
                keys, "maximo-oauth2", "maximo-api", "primary-test-key", 1800, clock);

        String token = issuer.issueToken("test-client", Map.of());

        assertThrows(JwtException.class, () -> validator.parseAuthorizationHeader("Bearer " + token));
    }

    @Test
    void rejectsExcessiveTokenLifetime() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();

        assertThrows(IllegalStateException.class, () -> new JwtTokenService(
                keys,
                "maximo-oauth2",
                "maximo-api",
                "primary-test-key",
                3601,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static JwtTokenService service(String audience) throws Exception {
        return new JwtTokenService(
                JwtKeyTestFactory.create(),
                "maximo-oauth2",
                audience,
                "primary-test-key",
                1800,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
