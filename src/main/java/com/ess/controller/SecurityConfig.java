package com.ess.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable() // Disable CSRF for simplicity (not recommended for production)
            .authorizeRequests()
       //     .antMatchers("/api/generate-token").permitAll() // Allow unauthenticated access
       //     .anyRequest().authenticated() // Require authentication for all other endpoints
            .anyRequest().permitAll()
            .and()
            .httpBasic(); // Enable basic authentication

        return http.build();
    }
}
