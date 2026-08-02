package com.ess.controller;

import com.ess.security.JwtKeyProvider;
import com.ess.support.JwtKeyTestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenControllerSecurityTest {

    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";
    private static final String MAXIMO_BASE_URL = "https://maximo.example.com/maximo/api/script";

    @Test
    void sharedSigningKeysKeepTokensValidAcrossApplicationInstances() throws Exception {
        KeyPair sharedKeyPair = JwtKeyTestFactory.generate(2048);
        TokenController issuer = controller(JwtKeyTestFactory.create(sharedKeyPair));
        TokenController validator = controller(JwtKeyTestFactory.create(sharedKeyPair));

        Map<String, String> credentials = new HashMap<>();
        credentials.put("client_id", CLIENT_ID);
        credentials.put("client_secret", CLIENT_SECRET);

        ResponseEntity<?> response = issuer.token(credentials, null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        String token = (String) ((Map<String, Object>) response.getBody()).get("token");
        assertNotNull(token);
        assertEquals("The Token is valid for user: " + CLIENT_ID,
                validator.validateToken("Bearer " + token));
    }

    @Test
    void rejectsMissingRequiredRuntimeConfiguration() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();

        assertThrows(IllegalStateException.class,
                () -> new TokenController(CLIENT_ID, "", MAXIMO_BASE_URL, "api-key", keys));
        assertThrows(IllegalStateException.class,
                () -> new TokenController(CLIENT_ID, CLIENT_SECRET, MAXIMO_BASE_URL, "", keys));
    }

    @Test
    void rejectsNonHttpsMaximoBaseUrl() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();

        assertThrows(IllegalStateException.class,
                () -> new TokenController(
                        CLIENT_ID,
                        CLIENT_SECRET,
                        "http://maximo.example.com/maximo/api/script",
                        "api-key",
                        keys));
    }

    private static TokenController controller(JwtKeyProvider keys) {
        return new TokenController(CLIENT_ID, CLIENT_SECRET, MAXIMO_BASE_URL, "api-key", keys);
    }
}
