package com.billbuddy.backend.support;

import com.billbuddy.backend.common.EmailService;
import com.billbuddy.backend.features.auth.dto.request.ChangePasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.ForgotPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.RefreshTokenRequest;
import com.billbuddy.backend.features.auth.dto.request.ResetPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.request.VerifyEmailRequest;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthHardeningFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    private String signup(String emailPrefix) throws Exception {
        String email = emailPrefix + "-" + System.nanoTime() + "@example.com";
        SignupRequest request = new SignupRequest();
        request.setFullName("Test User");
        request.setEmail(email);
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email, String password, String deviceId) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setDeviceId(deviceId);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String extractToken(String link) {
        return URI.create(link).getQuery().replace("token=", "");
    }

    @Test
    void signup_sendsVerificationEmail_andTokenVerifiesSuccessfully() throws Exception {
        String email = signup("verify");
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getEmailVerifiedAt()).isNull();

        var linkCaptor = forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), linkCaptor.capture());
        String token = extractToken(linkCaptor.getValue());

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setToken(token);
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk());

        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.getEmailVerifiedAt()).isNotNull();

        // reusing the same (now-used) token fails
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_noOpsWithFriendlyMessage_whenAlreadyVerified() throws Exception {
        String email = signup("resend");
        var linkCaptor = forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), linkCaptor.capture());
        String token = extractToken(linkCaptor.getValue());

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setToken(token);
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk());

        String accessToken = extractAccessToken(login(email, "password123", "device-1"));

        reset(emailService);
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value("Email already verified"));

        verifyNoInteractions(emailService);
    }

    @Test
    void passwordReset_revokesOldSessionAndAllowsLoginWithNewPassword() throws Exception {
        String email = signup("reset");
        String deviceId = "device-reset";
        String oldRefreshToken = extractRefreshToken(login(email, "password123", deviceId));

        // request reset for a nonexistent email -- still 200, no enumeration leak
        ForgotPasswordRequest fakeRequest = new ForgotPasswordRequest();
        fakeRequest.setEmail("nobody-" + System.nanoTime() + "@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fakeRequest)))
                .andExpect(status().isOk());

        ForgotPasswordRequest realRequest = new ForgotPasswordRequest();
        realRequest.setEmail(email);
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(realRequest)))
                .andExpect(status().isOk());

        var linkCaptor = forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), linkCaptor.capture());
        String token = extractToken(linkCaptor.getValue());

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken(token);
        resetRequest.setNewPassword("newpassword456");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        // old password no longer works
        LoginRequest oldLogin = new LoginRequest();
        oldLogin.setEmail(email);
        oldLogin.setPassword("password123");
        oldLogin.setDeviceId(deviceId);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // the refresh token issued before the reset is now revoked
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(oldRefreshToken);
        refreshRequest.setDeviceId(deviceId);
        mockMvc.perform(post("/api/v1/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());

        // new password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                loginRequestFor(email, "newpassword456", deviceId))))
                .andExpect(status().isOk());

        // reusing the reset token fails
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_revokesOtherSessions_andRequiresCorrectCurrentPassword() throws Exception {
        String email = signup("change");
        String deviceId = "device-change";
        String loginBody = login(email, "password123", deviceId);
        String accessToken = extractAccessToken(loginBody);
        String oldRefreshToken = extractRefreshToken(loginBody);

        ChangePasswordRequest wrongCurrent = new ChangePasswordRequest();
        wrongCurrent.setCurrentPassword("wrongpassword");
        wrongCurrent.setNewPassword("newpassword456");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrent)))
                .andExpect(status().isUnauthorized());

        ChangePasswordRequest correctChange = new ChangePasswordRequest();
        correctChange.setCurrentPassword("password123");
        correctChange.setNewPassword("newpassword456");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctChange)))
                .andExpect(status().isOk());

        // old session revoked
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(oldRefreshToken);
        refreshRequest.setDeviceId(deviceId);
        mockMvc.perform(post("/api/v1/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());

        // new password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                loginRequestFor(email, "newpassword456", deviceId))))
                .andExpect(status().isOk());
    }

    private LoginRequest loginRequestFor(String email, String password, String deviceId) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setDeviceId(deviceId);
        return request;
    }

    private String extractAccessToken(String loginResponseBody) throws Exception {
        return objectMapper.readTree(loginResponseBody).get("accessToken").asText();
    }

    private String extractRefreshToken(String loginResponseBody) throws Exception {
        return objectMapper.readTree(loginResponseBody).get("refreshToken").asText();
    }
}
