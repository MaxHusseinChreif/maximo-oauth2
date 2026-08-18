package com.ess.security;

import com.ess.support.JwtKeyTestFactory;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidBearerToken() throws Exception {
        JwtTokenService tokens = tokens();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens);
        MockHttpServletRequest request = request("/api/CBSAPI");
        request.addHeader("Authorization", "Bearer " + tokens.issueToken("test-client", Map.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(request, response, chain);

        assertNotNull(authentication.get());
        assertEquals("test-client", authentication.get().getName());
    }

    @Test
    void rejectsInvalidBearerTokenWithoutCallingController() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens());
        MockHttpServletRequest request = request("/api/CBSAPI");
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_token"));
        assertFalse(response.getContentAsString().contains("invalid\""));
    }

    @Test
    void leavesTokenEndpointPublic() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens());
        MockHttpServletRequest request = request("/api/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static JwtTokenService tokens() throws Exception {
        return new JwtTokenService(
                JwtKeyTestFactory.create(),
                "maximo-oauth2",
                "maximo-api",
                "test-key",
                1800);
    }
}
