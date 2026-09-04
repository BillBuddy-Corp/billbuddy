package com.billbuddy.backend.features.auth.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.features.auth.dto.request.UpdateProfileRequest;
import com.billbuddy.backend.features.auth.dto.response.UserProfileResponse;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.auth.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid-access-token";

    private void stubValidAccessToken(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(claims.get("email", String.class)).thenReturn("jane@example.com");
        when(jwtService.parseAndValidate(VALID_TOKEN)).thenReturn(claims);
    }

    private UserProfileResponse sampleResponse() {
        return new UserProfileResponse(1L, "Jane Doe", "jane@example.com", null, false, null, "INR", LocalDateTime.now());
    }

    @Test
    void getProfile_returns200() throws Exception {
        stubValidAccessToken(1L);
        when(userService.getProfile(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void getProfile_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_returns200() throws Exception {
        stubValidAccessToken(1L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("INR");

        when(userService.updateProfile(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_returns400_whenFullNameBlank() throws Exception {
        stubValidAccessToken(1L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("");
        request.setDefaultCurrency("INR");

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_returns400_whenServiceThrowsInvalidCurrency() throws Exception {
        stubValidAccessToken(1L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Doe");
        request.setDefaultCurrency("ZZZ");

        when(userService.updateProfile(eq(1L), any()))
                .thenThrow(new InvalidCurrencyException("Invalid currency code: ZZZ"));

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
