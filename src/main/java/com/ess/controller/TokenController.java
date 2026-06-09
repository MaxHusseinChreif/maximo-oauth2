package com.ess.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;
import okhttp3.*;
import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.TrustManager;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 
 * @author M.Alayoubi
 *         ip:8080/api/generate-token -> access token
 * 
 */
@RestController
@RequestMapping("/api")
public class TokenController {

    private static final PrivateKey PRIVATE_KEY;
    private static final PublicKey PUBLIC_KEY;

    static {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PRIVATE_KEY = keyPair.getPrivate();
            PUBLIC_KEY = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RSA KeyPair", e);
        }
    }

    private static final long EXPIRATION_TIME = 30 * 60 * 1000; // 30 minutes in milliseconds
    // Build the insecure OkHttp client
    private final OkHttpClient httpClient = getUnsafeOkHttpClient();

    private Jws<Claims> parseToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Jwts.parser()
                .setSigningKey(PUBLIC_KEY)
                .parseClaimsJws(token);
    }

    private ResponseEntity<String> unauthorizedTokenResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid token. You need to generate a new token.");
    }

    /**
     * 
     * @param username
     * @return
     */
    @GetMapping("/generate-token")
    public Map<String, String> generateToken(String username) {
        if (!"maxinst".equalsIgnoreCase(username))
            throw new RuntimeException("Invalid Username " + username + ".");

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(io.jsonwebtoken.SignatureAlgorithm.RS256, PRIVATE_KEY)
                .compact();

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("expiry", "" + EXPIRATION_TIME);
        return response;
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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/CBSAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/ITEMAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/MATUSETRANSAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/ASSETAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/INVENTORYAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/POAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/SERVITEMAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/VENDORAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/VENDORP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/ITEMSERVP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/INVENTORYP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/INVUSEP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/POP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/PERSONAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/SHIFTAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/LABORSHIFTAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/MODAVAILAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/QUALIFICATIONAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/LABORQUALAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/LABORCERTAPI")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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
            Request request = new Request.Builder()
                    .url("https://maxdev.manage.maxdev.apps.me-qhscactm.dev.openshift.sevenit.cloud/maximo/api/script/ASSETP1API")
                    .addHeader("apikey", "themlgciqgkh4p5tlk7dat2shbacc11eop7kft6a")
                    .post(body)
                    .build();

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

    // Bypass SSL certificate validation and enforce TLS 1.2+
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new javax.net.ssl.X509ExtendedTrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[] {};
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket)
                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket)
                                throws CertificateException {
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine)
                                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine)
                                                throws CertificateException {
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                    .build();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectionSpecs(java.util.Collections.singletonList(spec))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
