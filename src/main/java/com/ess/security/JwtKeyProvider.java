package com.ess.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public final class JwtKeyProvider {

    private static final int MINIMUM_RSA_KEY_SIZE = 2048;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtKeyProvider(
            @Value("${app.jwt.private-key-location}") Resource privateKeyResource,
            @Value("${app.jwt.public-key-location}") Resource publicKeyResource) {
        try {
            this.privateKey = loadPrivateKey(privateKeyResource);
            this.publicKey = loadPublicKey(publicKeyResource);
            validateKeyPair(this.privateKey, this.publicKey);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load the configured JWT RSA key pair", exception);
        }
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    private static PrivateKey loadPrivateKey(Resource resource) throws IOException, GeneralSecurityException {
        byte[] encodedKey = decodePem(resource, "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
    }

    private static PublicKey loadPublicKey(Resource resource) throws IOException, GeneralSecurityException {
        byte[] encodedKey = decodePem(resource, "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
    }

    private static byte[] decodePem(Resource resource, String keyType) throws IOException {
        String pem;
        try (InputStream inputStream = resource.getInputStream()) {
            pem = new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
        }

        String beginMarker = "-----BEGIN " + keyType + "-----";
        String endMarker = "-----END " + keyType + "-----";
        if (!pem.contains(beginMarker) || !pem.contains(endMarker)) {
            throw new IllegalArgumentException("Expected a PEM encoded " + keyType);
        }

        String encoded = pem
                .replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }

    private static void validateKeyPair(PrivateKey privateKey, PublicKey publicKey) {
        if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)
                || !(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalArgumentException("JWT signing keys must be RSA keys");
        }

        if (rsaPrivateKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE) {
            throw new IllegalArgumentException("JWT RSA keys must be at least 2048 bits");
        }

        if (!rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus())) {
            throw new IllegalArgumentException("JWT private and public keys do not form a matching pair");
        }
    }
}
