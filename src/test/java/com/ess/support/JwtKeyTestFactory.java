package com.ess.support;

import com.ess.security.JwtKeyProvider;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class JwtKeyTestFactory {

    private JwtKeyTestFactory() {
    }

    public static KeyPair generate(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    public static JwtKeyProvider create() throws Exception {
        return create(generate(2048));
    }

    public static JwtKeyProvider create(KeyPair keyPair) {
        return create(keyPair, keyPair);
    }

    public static JwtKeyProvider create(KeyPair privateKeyPair, KeyPair publicKeyPair) {
        ByteArrayResource privateKey = resource("PRIVATE KEY", privateKeyPair.getPrivate().getEncoded());
        ByteArrayResource publicKey = resource("PUBLIC KEY", publicKeyPair.getPublic().getEncoded());
        return new JwtKeyProvider(privateKey, publicKey);
    }

    private static ByteArrayResource resource(String type, byte[] encodedKey) {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encodedKey);
        String pem = "-----BEGIN " + type + "-----\n"
                + encoded
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
