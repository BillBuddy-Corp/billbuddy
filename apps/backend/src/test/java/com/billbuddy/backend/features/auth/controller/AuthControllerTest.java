package com.billbuddy.backend.features.auth.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.billbuddy.backend.exception.UserAlreadyExistsException;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.LogoutRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.response.LoginResponse;
import com.billbuddy.backend.features.auth.dto.response.SessionResponse;
import com.billbuddy.backend.features.auth.dto.response.SignupResponse;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    // ===================== SIGNUP =====================

    @Test
    void signup_returns201_whenRequestValid() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setPassword("password123");

        when(authService.signup(any())).thenReturn(
                new SignupResponse(1L, "jane@example.com", "Jane Doe", LocalDateTime.now())
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void signup_returns400_whenEmailBlank() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("Jane Doe");
        request.setEmail("");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_returns409_whenEmailAlreadyRegistered() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setFullName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setPassword("password123");

        when(authService.signup(any())).thenThrow(new UserAlreadyExistsException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ===================== LOGIN =====================

    @Test
    void login_returns200WithTokens_whenCredentialsValid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setDeviceId("device-abc");

        when(authService.login(any(), any())).thenReturn(
                new LoginResponse("Bearer", "access-token", "refresh-token", 1L, "jane@example.com", "Jane Doe")
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void login_returns401_whenCredentialsInvalid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong-password");
        request.setDeviceId("device-abc");

        when(authService.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ===================== LOGOUT =====================

    @Test
    void logout_returns403_whenNoAuthorizationHeaderProvided() throws Exception {
        // SecurityConfig has no explicit AuthenticationEntryPoint, so Spring Security
        // falls back to Http403ForbiddenEntryPoint for unauthenticated access here.
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("some-refresh-token");
        request.setDeviceId("device-abc");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_returns200_whenAuthorizationHeaderValid() throws Exception {
        stubValidAccessToken(1L);

        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("some-refresh-token");
        request.setDeviceId("device-abc");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).logout("some-refresh-token", "device-abc");
    }

    // ===================== LOGOUT ALL =====================

    @Test
    void logoutAll_returns200_andDelegatesWithUserIdFromToken() throws Exception {
        stubValidAccessToken(1L);

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(authService).logoutAllDevices(1L);
    }

    // ===================== SESSIONS =====================

    @Test
    void sessions_returns200WithSessionList_whenAuthorizedWithDeviceHeader() throws Exception {
        stubValidAccessToken(1L);

        when(authService.listSessions(eq(1L), eq("device-abc"))).thenReturn(
                List.of(new SessionResponse(
                        10L, "device-abc", "iPhone 15", "127.0.0.1", "Mozilla/5.0",
                        LocalDateTime.now(), LocalDateTime.now().plusDays(30), true
                ))
        );

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .header("X-Device-Id", "device-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].deviceId").value("device-abc"))
                .andExpect(jsonPath("$.sessions[0].current").value(true));
    }
}
