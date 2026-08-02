package com.ess.controller;

import com.ess.security.JwtKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author M.Alayoubi
 *         ip:8080/api/token -> access token (OAuth2 Client Credentials)
 * 
 */
@RestController
@RequestMapping("/api")
public class TokenController {

    private final String configuredClientId;
    private final String configuredClientSecret;
    private final String maximoApiKey;
    private final HttpUrl maximoScriptBaseUrl;
    private final JwtKeyProvider jwtKeyProvider;

    private static final long EXPIRATION_TIME = 30 * 60 * 1000; // 30 minutes in milliseconds
    private final OkHttpClient httpClient = createHttpClient();

    public TokenController(
            @Value("${app.oauth.client-id}") String configuredClientId,
            @Value("${app.oauth.client-secret}") String configuredClientSecret,
            @Value("${app.maximo.script-base-url}") String maximoScriptBaseUrl,
            @Value("${app.maximo.api-key}") String maximoApiKey,
            JwtKeyProvider jwtKeyProvider) {
        this.configuredClientId = requireConfiguration("OAUTH_CLIENT_ID", configuredClientId);
        this.configuredClientSecret = requireConfiguration("OAUTH_CLIENT_SECRET", configuredClientSecret);
        this.maximoApiKey = requireConfiguration("MAXIMO_API_KEY", maximoApiKey);
        this.maximoScriptBaseUrl = parseMaximoScriptBaseUrl(maximoScriptBaseUrl);
        this.jwtKeyProvider = jwtKeyProvider;
    }

    private Jws<Claims> parseToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Jwts.parser()
                .setSigningKey(jwtKeyProvider.publicKey())
                .parseClaimsJws(token);
    }

    private ResponseEntity<String> unauthorizedTokenResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid token. You need to generate a new token.");
    }

    /**
     * OAuth2 Client Credentials Token Endpoint
     * Accepts client_id & client_secret via form parameters, query params, or JSON body.
     */
    @PostMapping(value = "/token", consumes = org.springframework.http.MediaType.ALL_VALUE)
    public ResponseEntity<?> token(
            @RequestParam Map<String, String> allParams,
            @RequestBody(required = false) String rawBody) {

        String clientId = allParams.get("client_id");
        String clientSecret = allParams.get("client_secret");

        if ((clientId == null || clientSecret == null) && rawBody != null && !rawBody.trim().isEmpty()) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(rawBody).getAsJsonObject();
                if (clientId == null && json.has("client_id")) {
                    clientId = json.get("client_id").getAsString();
                }
                if (clientSecret == null && json.has("client_secret")) {
                    clientSecret = json.get("client_secret").getAsString();
                }
            } catch (Exception ignored) {
            }
        }

        if (clientId == null || clientSecret == null) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "invalid_request");
            err.put("error_description", "client_id and client_secret are required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        if (!configuredClientId.equals(clientId) || !configuredClientSecret.equals(clientSecret)) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "invalid_client");
            err.put("error_description", "Invalid client_id or client_secret.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
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

        Map<String, Object> response = new HashMap<>();
        // response.put("access_token", token);
        response.put("token_type", "Bearer");
        response.put("expires_in", EXPIRATION_TIME / 1000);
        response.put("token", token); // Backward compatibility key
        response.put("expiry", "" + EXPIRATION_TIME);
        return ResponseEntity.ok(response);
    }

    /**
     * 
     * @param token
     * @return
     */
    @GetMapping("/validate-token")
    public String validateToken(@RequestHeader("Authorization") String token) {
        try {
            Claims claims = parseToken(token).getBody();

            // Token is valid, return "Hello World"
            return "The Token is valid for user: " + claims.get("username");

        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            throw new RuntimeException("Invalid token. You need to generate a new token.");
        }
    }

    /**
     * 
     * @param user
     * @return
     *         ip:8080/api/CBSAPI
     */
    @PostMapping("/CBSAPI")
    public ResponseEntity<?> cbsApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("CBSAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/ITEMAPI")
    public ResponseEntity<?> itemApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("ITEMAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/MATUSETRANSAPI")
    public ResponseEntity<?> MatusetransApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("MATUSETRANSAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/ASSETAPI")
    public ResponseEntity<?> AssetApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("ASSETAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/INVENTORYAPI")
    public ResponseEntity<?> InventoryApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("INVENTORYAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/POAPI")
    public ResponseEntity<?> PoApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("POAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/SERVITEMAPI")
    public ResponseEntity<?> ServItemApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("SERVITEMAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/VENDORAPI")
    public ResponseEntity<?> VendorApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("VENDORAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/VENDORP1API")
    public ResponseEntity<?> VendorP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("VENDORP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/ITEMSERVP1API")
    public ResponseEntity<?> ItemServP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("ITEMSERVP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/INVENTORYP1API")
    public ResponseEntity<?> InventoryP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("INVENTORYP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/INVUSEP1API")
    public ResponseEntity<?> InvUseP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("INVUSEP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/POP1API")
    public ResponseEntity<?> PoP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("POP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/PERSONAPI")
    public ResponseEntity<?> PersonApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("PERSONAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/SHIFTAPI")
    public ResponseEntity<?> ShiftApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("SHIFTAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/LABORSHIFTAPI")
    public ResponseEntity<?> LaborShiftApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("LABORSHIFTAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/MODAVAILAPI")
    public ResponseEntity<?> ModAvailApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("MODAVAILAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/QUALIFICATIONAPI")
    public ResponseEntity<?> QualificationApi(@RequestHeader("Authorization") String token,
            @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("QUALIFICATIONAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/LABORQUALAPI")
    public ResponseEntity<?> LaborQualApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("LABORQUALAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/LABORCERTAPI")
    public ResponseEntity<?> LaborCertApi(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("LABORCERTAPI", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * 
     * @param user
     * @return
     */
    @PostMapping("/ASSETP1API")
    public ResponseEntity<?> AssetP1Api(@RequestHeader("Authorization") String token, @RequestBody String reqBody) {
        try {
            Jws<Claims> claims = parseToken(token);
            claims.getSignature();

            // Create the request body with JSON content
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    MediaType.parse("application/json"),
                    reqBody);

            // Build the request
            Request request = createMaximoRequest("ASSETP1API", body);

            // Execute the request
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.body().string());
            } else {
                // Return an error message if the request failed
                return ResponseEntity.status(response.code())
                        .body(response.body().string());
            }
        } catch (SignatureException | io.jsonwebtoken.ExpiredJwtException e) {
            // Token is invalid or expired
            return unauthorizedTokenResponse();
        } catch (IOException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * This is used to handle the error generated from APIs
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleInvalidTokenException(RuntimeException ex) {
        // Return JSON with error message
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.EXPECTATION_FAILED);
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

    private static HttpUrl parseMaximoScriptBaseUrl(String configuredUrl) {
        String baseUrl = requireConfiguration("MAXIMO_SCRIPT_BASE_URL", configuredUrl);
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        HttpUrl parsedUrl;
        try {
            parsedUrl = HttpUrl.get(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MAXIMO_SCRIPT_BASE_URL must be a valid URL", exception);
        }

        if (!"https".equalsIgnoreCase(parsedUrl.scheme())) {
            throw new IllegalStateException("MAXIMO_SCRIPT_BASE_URL must use HTTPS");
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
                .connectionSpecs(java.util.Collections.singletonList(tlsSpec))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
