package com.ess.controller;

import com.ess.security.JwtKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.TlsVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Issues OAuth2 client-credentials tokens and proxies authenticated requests to
 * the configured Maximo script endpoints.
 */
@RestController
@RequestMapping("/api")
public class TokenController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenController.class);
    private static final long EXPIRATION_TIME = 30 * 60 * 1000;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final String configuredClientId;
    private final String configuredClientSecret;
    private final String maximoApiKey;
    private final HttpUrl maximoScriptBaseUrl;
    private final JwtKeyProvider jwtKeyProvider;
    private final OkHttpClient httpClient;

    @Autowired
    public TokenController(
            @Value("${app.oauth.client-id}") String configuredClientId,
            @Value("${app.oauth.client-secret}") String configuredClientSecret,
            @Value("${app.maximo.script-base-url}") String maximoScriptBaseUrl,
            @Value("${app.maximo.api-key}") String maximoApiKey,
            JwtKeyProvider jwtKeyProvider) {
        this(
                configuredClientId,
                configuredClientSecret,
                parseMaximoScriptBaseUrl(maximoScriptBaseUrl),
                maximoApiKey,
                jwtKeyProvider,
                createHttpClient());
    }

    TokenController(
            String configuredClientId,
            String configuredClientSecret,
            HttpUrl maximoScriptBaseUrl,
            String maximoApiKey,
            JwtKeyProvider jwtKeyProvider,
            OkHttpClient httpClient) {
        this.configuredClientId = requireConfiguration("OAUTH_CLIENT_ID", configuredClientId);
        this.configuredClientSecret = requireConfiguration("OAUTH_CLIENT_SECRET", configuredClientSecret);
        this.maximoApiKey = requireConfiguration("MAXIMO_API_KEY", maximoApiKey);
        this.maximoScriptBaseUrl = Objects.requireNonNull(maximoScriptBaseUrl, "maximoScriptBaseUrl");
        this.jwtKeyProvider = Objects.requireNonNull(jwtKeyProvider, "jwtKeyProvider");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /**
     * OAuth2 client-credentials endpoint. Credentials may be supplied as form,
     * query, or JSON parameters for backward compatibility.
     */
    @PostMapping(value = "/token", consumes = org.springframework.http.MediaType.ALL_VALUE)
    public ResponseEntity<?> token(
            @RequestParam Map<String, String> allParams,
            @RequestBody(required = false) String rawBody) {
        String clientId = allParams.get("client_id");
        String clientSecret = allParams.get("client_secret");

        if ((clientId == null || clientSecret == null) && rawBody != null && !rawBody.isBlank()) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(rawBody).getAsJsonObject();
                if (clientId == null && json.has("client_id")) {
                    clientId = json.get("client_id").getAsString();
                }
                if (clientSecret == null && json.has("client_secret")) {
                    clientSecret = json.get("client_secret").getAsString();
                }
            } catch (RuntimeException ignored) {
                // The response below deliberately does not expose JSON parsing details.
            }
        }

        if (clientId == null || clientSecret == null) {
            LOGGER.warn("event=oauth_token outcome=denied reason=missing_credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("invalid_request", "client_id and client_secret are required."));
        }

        if (!configuredClientId.equals(clientId) || !configuredClientSecret.equals(clientSecret)) {
            LOGGER.warn("event=oauth_token outcome=denied reason=invalid_credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("invalid_client", "Invalid client_id or client_secret."));
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", clientId);

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(clientId)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(io.jsonwebtoken.SignatureAlgorithm.RS256, jwtKeyProvider.privateKey())
                .compact();

        LOGGER.info("event=oauth_token outcome=success");

        Map<String, Object> response = new HashMap<>();
        response.put("token_type", "Bearer");
        response.put("expires_in", EXPIRATION_TIME / 1000);
        response.put("token", token);
        response.put("expiry", Long.toString(EXPIRATION_TIME));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-token")
    public String validateToken(@RequestHeader("Authorization") String token) {
        try {
            Claims claims = parseToken(token).getBody();
            LOGGER.info("event=token_validation outcome=success");
            return "The Token is valid for user: " + claims.get("username");
        } catch (JwtException | IllegalArgumentException exception) {
            LOGGER.warn("event=token_validation outcome=denied");
            throw new InvalidTokenException();
        }
    }

    private ResponseEntity<?> forwardToMaximo(String scriptName, String token, String reqBody) {
        try {
            parseToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            LOGGER.warn("event=maximo_proxy script={} outcome=denied reason=invalid_token", scriptName);
            return unauthorizedTokenResponse();
        }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(JSON_MEDIA_TYPE, reqBody);
        Request request = createMaximoRequest(scriptName, body);

        try (Response response = httpClient.newCall(request).execute()) {
            int status = response.code();
            ResponseBody responseBody = response.body();
            String payload = responseBody == null ? "" : responseBody.string();

            if (response.isSuccessful()) {
                LOGGER.info("event=maximo_proxy script={} outcome=success status={}", scriptName, status);
                return ResponseEntity.ok(payload);
            }

            LOGGER.warn("event=maximo_proxy script={} outcome=upstream_error status={}", scriptName, status);
            return ResponseEntity.status(status).body(payload);
        } catch (IOException exception) {
            LOGGER.error(
                    "event=maximo_proxy script={} outcome=transport_error exception_type={}",
                    scriptName,
                    exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorResponse("upstream_unavailable", "Unable to contact the upstream service."));
        }
    }

    @PostMapping("/CBSAPI")
    public ResponseEntity<?> cbsApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("CBSAPI", token, reqBody);
    }

    @PostMapping("/ITEMAPI")
    public ResponseEntity<?> itemApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("ITEMAPI", token, reqBody);
    }

    @PostMapping("/MATUSETRANSAPI")
    public ResponseEntity<?> MatusetransApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("MATUSETRANSAPI", token, reqBody);
    }

    @PostMapping("/ASSETAPI")
    public ResponseEntity<?> AssetApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("ASSETAPI", token, reqBody);
    }

    @PostMapping("/INVENTORYAPI")
    public ResponseEntity<?> InventoryApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("INVENTORYAPI", token, reqBody);
    }

    @PostMapping("/POAPI")
    public ResponseEntity<?> PoApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("POAPI", token, reqBody);
    }

    @PostMapping("/SERVITEMAPI")
    public ResponseEntity<?> ServItemApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("SERVITEMAPI", token, reqBody);
    }

    @PostMapping("/VENDORAPI")
    public ResponseEntity<?> VendorApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("VENDORAPI", token, reqBody);
    }

    @PostMapping("/VENDORP1API")
    public ResponseEntity<?> VendorP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("VENDORP1API", token, reqBody);
    }

    @PostMapping("/ITEMSERVP1API")
    public ResponseEntity<?> ItemServP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("ITEMSERVP1API", token, reqBody);
    }

    @PostMapping("/INVENTORYP1API")
    public ResponseEntity<?> InventoryP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("INVENTORYP1API", token, reqBody);
    }

    @PostMapping("/INVUSEP1API")
    public ResponseEntity<?> InvUseP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("INVUSEP1API", token, reqBody);
    }

    @PostMapping("/POP1API")
    public ResponseEntity<?> PoP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("POP1API", token, reqBody);
    }

    @PostMapping("/PERSONAPI")
    public ResponseEntity<?> PersonApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("PERSONAPI", token, reqBody);
    }

    @PostMapping("/SHIFTAPI")
    public ResponseEntity<?> ShiftApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("SHIFTAPI", token, reqBody);
    }

    @PostMapping("/LABORSHIFTAPI")
    public ResponseEntity<?> LaborShiftApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("LABORSHIFTAPI", token, reqBody);
    }

    @PostMapping("/MODAVAILAPI")
    public ResponseEntity<?> ModAvailApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("MODAVAILAPI", token, reqBody);
    }

    @PostMapping("/QUALIFICATIONAPI")
    public ResponseEntity<?> QualificationApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("QUALIFICATIONAPI", token, reqBody);
    }

    @PostMapping("/LABORQUALAPI")
    public ResponseEntity<?> LaborQualApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("LABORQUALAPI", token, reqBody);
    }

    @PostMapping("/LABORCERTAPI")
    public ResponseEntity<?> LaborCertApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("LABORCERTAPI", token, reqBody);
    }

    @PostMapping("/ASSETP1API")
    public ResponseEntity<?> AssetP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        return forwardToMaximo("ASSETP1API", token, reqBody);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<?> handleInvalidTokenException() {
        return unauthorizedTokenResponse();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleUnexpectedRuntimeException(RuntimeException exception) {
        LOGGER.error(
                "event=request_processing outcome=error exception_type={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("request_failed", "The request could not be processed."));
    }

    private Jws<Claims> parseToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        String compactToken = authorizationHeader.substring(7).trim();
        if (compactToken.isEmpty()) {
            throw new IllegalArgumentException("Bearer token is required");
        }

        return Jwts.parser()
                .setSigningKey(jwtKeyProvider.publicKey())
                .parseClaimsJws(compactToken);
    }

    private ResponseEntity<String> unauthorizedTokenResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid token. You need to generate a new token.");
    }

    private Request createMaximoRequest(String scriptName, okhttp3.RequestBody body) {
        HttpUrl scriptUrl = maximoScriptBaseUrl.resolve(scriptName);
        if (scriptUrl == null) {
            throw new IllegalArgumentException("Invalid Maximo script name");
        }

        return new Request.Builder()
                .url(scriptUrl)
                .addHeader("apikey", maximoApiKey)
                .post(body)
                .build();
    }

    private static Map<String, String> errorResponse(String error, String description) {
        Map<String, String> response = new HashMap<>();
        response.put("error", error);
        response.put("error_description", description);
        return response;
    }

    private static HttpUrl parseMaximoScriptBaseUrl(String configuredUrl) {
        String baseUrl = requireConfiguration("MAXIMO_SCRIPT_BASE_URL", configuredUrl);
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        HttpUrl parsedUrl = HttpUrl.parse(baseUrl);
        if (parsedUrl == null || !"https".equalsIgnoreCase(parsedUrl.scheme())) {
            throw new IllegalStateException("MAXIMO_SCRIPT_BASE_URL must be a valid HTTPS URL");
        }
        return parsedUrl;
    }

    private static String requireConfiguration(String variableName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured");
        }
        return value;
    }

    private static OkHttpClient createHttpClient() {
        ConnectionSpec tlsSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .build();

        return new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(tlsSpec))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static final class InvalidTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
