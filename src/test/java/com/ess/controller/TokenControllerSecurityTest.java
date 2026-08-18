package com.ess.controller;

import com.ess.security.JwtKeyProvider;
import com.ess.security.JwtTokenService;
import com.ess.support.JwtKeyTestFactory;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TokenControllerSecurityTest {

    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";
    private static final String MAXIMO_BASE_URL = "https://maximo.example.com/maximo/api/script";

    @Test
    void sharedSigningKeysKeepTokensValidAcrossApplicationInstances() throws Exception {
        KeyPair sharedKeyPair = JwtKeyTestFactory.generate(2048);
        TokenController issuer = controller(JwtKeyTestFactory.create(sharedKeyPair));
        TokenController validator = controller(JwtKeyTestFactory.create(sharedKeyPair));

        ResponseEntity<?> response = issuer.tokenJson(new TokenController.TokenRequest(CLIENT_ID, CLIENT_SECRET));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String token = (String) body.get("token");
        assertNotNull(token);
        assertEquals(token, body.get("access_token"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst(HttpHeaders.PRAGMA));
        assertEquals("The Token is valid for user: " + CLIENT_ID,
                validator.validateToken("Bearer " + token));
    }

    @Test
    void rejectsMissingRequiredRuntimeConfiguration() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();
        JwtTokenService tokens = tokenService(keys);

        assertThrows(IllegalStateException.class,
                () -> new TokenController(CLIENT_ID, "", MAXIMO_BASE_URL, "api-key", tokens));
        assertThrows(IllegalStateException.class,
                () -> new TokenController(CLIENT_ID, CLIENT_SECRET, MAXIMO_BASE_URL, "", tokens));
    }

    @Test
    void rejectsNonHttpsMaximoBaseUrl() throws Exception {
        JwtKeyProvider keys = JwtKeyTestFactory.create();
        JwtTokenService tokens = tokenService(keys);

        assertThrows(IllegalStateException.class,
                () -> new TokenController(
                        CLIENT_ID,
                        CLIENT_SECRET,
                        "http://maximo.example.com/maximo/api/script",
                        "api-key",
                        tokens));
    }

    @Test
    void rejectsInvalidTokenBeforeContactingMaximo() throws Exception {
        AtomicInteger networkCalls = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    networkCalls.incrementAndGet();
                    return successfulResponse(chain.request(), "{}");
                })
                .build();
        TokenController controller = controller(JwtKeyTestFactory.create(), client);

        ResponseEntity<?> response = controller.cbsApi("Bearer invalid-token", "{}");

        assertEquals(401, response.getStatusCode().value());
        assertEquals(0, networkCalls.get());
    }

    @Test
    void proxiesUsingConfiguredUrlAndApiKey() throws Exception {
        AtomicReference<Request> capturedRequest = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    capturedRequest.set(chain.request());
                    return successfulResponse(chain.request(), "{\"result\":\"ok\"}");
                })
                .build();
        TokenController controller = controller(JwtKeyTestFactory.create(), client);

        ResponseEntity<?> response = controller.cbsApi(issueToken(controller), "{\"request\":true}");
        Request request = capturedRequest.get();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"result\":\"ok\"}", response.getBody());
        assertNotNull(request);
        assertEquals("/maximo/api/script/CBSAPI", request.url().encodedPath());
        assertEquals("api-key", request.header("apikey"));
    }

    @Test
    void returnsSanitizedResponseWhenMaximoIsUnavailable() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new IOException("sensitive internal transport details");
                })
                .build();
        TokenController controller = controller(JwtKeyTestFactory.create(), client);

        ResponseEntity<?> response = controller.cbsApi(issueToken(controller), "{}");

        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("upstream_unavailable", body.get("error"));
        assertEquals("Unable to contact the upstream service.", body.get("error_description"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void auditLogDoesNotExposeRejectedCredentials(CapturedOutput output) throws Exception {
        String rejectedClientId = "sensitive-client-id";
        String rejectedClientSecret = "sensitive-client-secret";
        TokenController controller = controller(JwtKeyTestFactory.create());
        ResponseEntity<?> response = controller.tokenJson(
                new TokenController.TokenRequest(rejectedClientId, rejectedClientSecret));

        assertEquals(401, response.getStatusCode().value());
        assertTrue(output.getAll().contains("event=oauth_token outcome=denied reason=invalid_credentials"));
        assertFalse(output.getAll().contains(rejectedClientId));
        assertFalse(output.getAll().contains(rejectedClientSecret));
    }

    @Test
    void doesNotAcceptCredentialsFromQueryString() throws Exception {
        TokenController controller = controller(JwtKeyTestFactory.create());

        org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .build()
                .perform(post("/api/token")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("client_secret", CLIENT_SECRET)
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsSanitizedBadRequestForMalformedJsonCredentials() throws Exception {
        TokenController controller = controller(JwtKeyTestFactory.create());

        org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .build()
                .perform(post("/api/token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    private static TokenController controller(JwtKeyProvider keys) {
        return new TokenController(CLIENT_ID, CLIENT_SECRET, MAXIMO_BASE_URL, "api-key", tokenService(keys));
    }

    private static TokenController controller(
            JwtKeyProvider keys,
            OkHttpClient httpClient) {
        HttpUrl maximoBaseUrl = HttpUrl.parse(MAXIMO_BASE_URL + "/");
        assertNotNull(maximoBaseUrl);
        return new TokenController(
                CLIENT_ID,
                CLIENT_SECRET,
                maximoBaseUrl,
                "api-key",
                tokenService(keys),
                httpClient);
    }

    private static String issueToken(TokenController controller) {
        ResponseEntity<?> response = controller.tokenJson(new TokenController.TokenRequest(CLIENT_ID, CLIENT_SECRET));
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return "Bearer " + body.get("token");
    }

    private static JwtTokenService tokenService(JwtKeyProvider keys) {
        return new JwtTokenService(keys, "maximo-oauth2", "maximo-api", "test-key", 1800);
    }

    private static Response successfulResponse(Request request, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build();
    }
}
