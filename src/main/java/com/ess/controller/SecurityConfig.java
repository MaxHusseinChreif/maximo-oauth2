package com.ess.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.ess.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.security.require-https}") boolean requireHttps) throws Exception {
        HttpsEnforcementFilter httpsEnforcementFilter = new HttpsEnforcementFilter(requireHttps);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/token").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeJsonError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "invalid_token",
                                "A valid bearer token is required."))
                        .accessDeniedHandler((request, response, exception) -> writeJsonError(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "access_denied",
                                "Access is denied.")))
                .addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class)
                .addFilterBefore(httpsEnforcementFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableContainerRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static class HttpsEnforcementFilter extends OncePerRequestFilter {

        private final boolean requireHttps;

        private HttpsEnforcementFilter(boolean requireHttps) {
            this.requireHttps = requireHttps;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            String originalProto = forwardedProto == null ? "" : forwardedProto.split(",", 2)[0].trim();
            boolean isSecure = request.isSecure() || "https".equalsIgnoreCase(originalProto);

            if (requireHttps && !isSecure) {
                writeJsonError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "https_required",
                        "HTTPS/TLS is required for all endpoints.");
                return;
            }

            filterChain.doFilter(request, response);
        }
    }

    private static void writeJsonError(
            HttpServletResponse response,
            int status,
            String error,
            String description) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}");
    }
}
