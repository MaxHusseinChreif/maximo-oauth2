package com.ess.main;

import com.ess.security.JwtKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "app.oauth.client-id=test-client",
        "app.oauth.client-secret=test-secret",
        "app.maximo.script-base-url=https://maximo.example.com/maximo/api/script",
        "app.maximo.api-key=test-api-key",
        "app.jwt.issuer=maximo-oauth2",
        "app.jwt.audience=maximo-api",
        "app.jwt.key-id=test-key",
        "app.jwt.expiration-seconds=1800",
        "app.security.require-https=false"
})
class ApplicationContextSecurityTest {

    @MockitoBean
    private JwtKeyProvider jwtKeyProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void startsWithBearerSecurityAndWithoutGeneratedBasicUser() {
        assertNotNull(securityFilterChain);
        assertFalse(applicationContext.getBeansOfType(UserDetailsService.class).containsKey("inMemoryUserDetailsManager"));
    }
}
