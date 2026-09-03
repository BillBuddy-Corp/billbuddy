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
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // PUBLIC AUTH ENDPOINTS
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refreshtoken",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email",
                                "/actuator/health"
                        ).permitAll()

                        // PROTECTED AUTH ENDPOINTS
                        .requestMatchers(
                                "/api/v1/auth/logout-all",
                                "/api/v1/auth/sessions",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/change-password",
                                "/api/v1/auth/verify-email/resend"
                        ).authenticated()

                        // PROTECTED GROUP ENDPOINTS
                        .requestMatchers(
                                "/api/v1/groups/**",
                                "/api/v1/invites/**"
                        ).authenticated()

                        // PROTECTED EXPENSE ENDPOINTS
                        .requestMatchers(
                                "/api/v1/expenses/**"
                        ).authenticated()

                        // PROTECTED SETTLEMENT ENDPOINTS
                        .requestMatchers(
                                "/api/v1/settlements/**"
                        ).authenticated()

                        // PROTECTED STORAGE / USER PROFILE ENDPOINTS
                        .requestMatchers(
                                "/api/v1/files/**",
                                "/api/v1/users/**"
                        ).authenticated()

                        // EVERYTHING ELSE
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

//                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
