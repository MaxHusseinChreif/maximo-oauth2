package com.ess.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new HttpsEnforcementFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    private static class HttpsEnforcementFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            boolean isBehindProxy = forwardedProto != null;
            boolean isSecure = request.isSecure() || "https".equalsIgnoreCase(forwardedProto);

            // Enforce HTTPS when running behind a proxy (OpenShift), while allowing http://localhost for local testing
            if (isBehindProxy && !isSecure) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"invalid_request\", \"error_description\": \"HTTPS/TLS is required for all endpoints.\"}");
                return;
            }

            filterChain.doFilter(request, response);
        }
    }
}
