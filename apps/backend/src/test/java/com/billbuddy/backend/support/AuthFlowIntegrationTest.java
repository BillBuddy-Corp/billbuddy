package com.billbuddy.backend.support;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.LogoutRequest;
import com.billbuddy.backend.features.auth.dto.request.RefreshTokenRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack auth walkthrough against a real (H2) database, exercising the actual
 * HTTP layer, security filter chain, service logic and persistence together.
 *
 * Runs against H2 rather than Postgres because this environment has no Docker,
 * so Testcontainers isn't available. Swap the datasource in
 * src/test/resources/application-test.yml for a Testcontainers Postgres setup
 * if you want DB-parity guarantees, e.g. in a CI environment with Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupLoginRefreshLogout_worksEndToEndAgainstRealStack() throws Exception {
        String email = "integration-" + System.nanoTime() + "@example.com";
        String deviceId = "integration-device-1";

        // ---- signup ----
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setFullName("Integration User");
        signupRequest.setEmail(email);
        signupRequest.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // ---- login ----
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("password123");
        loginRequest.setDeviceId(deviceId);
        loginRequest.setDeviceName("Integration Test Runner");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String firstRefreshToken = loginBody.get("refreshToken").asText();

        // ---- refresh (rotates the token) ----
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(firstRefreshToken);
        refreshRequest.setDeviceId(deviceId);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String secondAccessToken = refreshBody.get("accessToken").asText();
        String secondRefreshToken = refreshBody.get("refreshToken").asText();

        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // ---- reusing the rotated-out (first) refresh token must now fail ----
        RefreshTokenRequest reuseRequest = new RefreshTokenRequest();
        reuseRequest.setRefreshToken(firstRefreshToken);
        reuseRequest.setDeviceId(deviceId);

        mockMvc.perform(post("/api/v1/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reuseRequest)))
                .andExpect(status().isUnauthorized());

        // ---- logout using the current (second) refresh token ----
        LogoutRequest logoutRequest = new LogoutRequest();
        logoutRequest.setRefreshToken(secondRefreshToken);
        logoutRequest.setDeviceId(deviceId);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + secondAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk());

        // ---- session is gone, even though the access token itself hasn't expired yet ----
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + secondAccessToken)
                        .header("X-Device-Id", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isEmpty());
    }
}
