package com.ess.security;

import com.ess.support.JwtKeyTestFactory;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtKeyProviderTest {

    @Test
    void loadsMatchingRsaKeyPair() throws Exception {
        KeyPair keyPair = JwtKeyTestFactory.generate(2048);

        JwtKeyProvider provider = JwtKeyTestFactory.create(keyPair);

        assertEquals(keyPair.getPrivate(), provider.privateKey());
        assertEquals(keyPair.getPublic(), provider.publicKey());
    }

    @Test
    void rejectsMismatchedKeyPair() throws Exception {
        KeyPair privateKeyPair = JwtKeyTestFactory.generate(2048);
        KeyPair publicKeyPair = JwtKeyTestFactory.generate(2048);

        assertThrows(IllegalStateException.class,
                () -> JwtKeyTestFactory.create(privateKeyPair, publicKeyPair));
    }

    @Test
    void rejectsRsaKeysSmallerThan2048Bits() throws Exception {
        KeyPair weakKeyPair = JwtKeyTestFactory.generate(1024);

        assertThrows(IllegalStateException.class, () -> JwtKeyTestFactory.create(weakKeyPair));
    }
}
