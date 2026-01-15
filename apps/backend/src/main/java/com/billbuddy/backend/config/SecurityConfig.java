package com.billbuddy.backend.config;

import com.billbuddy.backend.features.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // PUBLIC AUTH ENDPOINTS
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refreshtoken",
                                "/api/v1/auth/logout",
                                "/actuator/health"
                        ).permitAll()

                        // PROTECTED AUTH ENDPOINTS
                        .requestMatchers(
                                "/api/v1/auth/logout-all",
                                "/api/v1/auth/sessions"
                        ).authenticated()

                        // EVERYTHING ELSE
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
