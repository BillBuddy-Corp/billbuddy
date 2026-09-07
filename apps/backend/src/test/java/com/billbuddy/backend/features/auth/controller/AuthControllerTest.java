package com.billbuddy.backend.features.auth.controller;

import com.billbuddy.backend.config.SecurityConfig;
import com.billbuddy.backend.exception.InvalidAuthTokenException;
import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.billbuddy.backend.exception.InvalidOtpException;
import com.billbuddy.backend.exception.MobileNumberNotSetException;
import com.billbuddy.backend.exception.UserAlreadyExistsException;
import com.billbuddy.backend.features.auth.dto.request.ChangePasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.ForgotPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.GoogleSignInRequest;
import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.LogoutRequest;
import com.billbuddy.backend.features.auth.dto.request.ResetPasswordRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.auth.dto.request.VerifyEmailRequest;
import com.billbuddy.backend.features.auth.dto.request.VerifyMobileRequest;
import com.billbuddy.backend.features.auth.dto.response.LoginResponse;
import com.billbuddy.backend.features.auth.dto.response.SessionResponse;
import com.billbuddy.backend.features.auth.dto.response.SignupResponse;
import com.billbuddy.backend.features.auth.security.JwtService;
import com.billbuddy.backend.features.auth.service.AuthService;
import com.billbuddy.backend.features.auth.service.AuthTokenService;
import com.billbuddy.backend.features.auth.service.MobileOtpService;
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
import static org.mockito.Mockito.doThrow;
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
    private AuthTokenService authTokenService;

    @MockBean
    private MobileOtpService mobileOtpService;

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

    // ===================== GOOGLE SIGN-IN =====================

    @Test
    void signInWithGoogle_returns200WithTokens_whenValid() throws Exception {
        GoogleSignInRequest request = new GoogleSignInRequest();
        request.setIdToken("valid-id-token");
        request.setDeviceId("device-abc");

        when(authService.signInWithGoogle(any(), any())).thenReturn(
                new LoginResponse("Bearer", "access-token", "refresh-token", 1L, "jane@example.com", "Jane Doe")
        );

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void signInWithGoogle_returns401_whenTokenInvalid() throws Exception {
        GoogleSignInRequest request = new GoogleSignInRequest();
        request.setIdToken("bad-token");
        request.setDeviceId("device-abc");

        when(authService.signInWithGoogle(any(), any()))
                .thenThrow(new InvalidCredentialsException("Invalid Google token"));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signInWithGoogle_returns409_whenEmailBelongsToPasswordAccount() throws Exception {
        GoogleSignInRequest request = new GoogleSignInRequest();
        request.setIdToken("valid-id-token");
        request.setDeviceId("device-abc");

        when(authService.signInWithGoogle(any(), any()))
                .thenThrow(new UserAlreadyExistsException("This email is already registered with a password. Please log in with your password instead."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void signInWithGoogle_returns400_whenIdTokenBlank() throws Exception {
        GoogleSignInRequest request = new GoogleSignInRequest();
        request.setIdToken("");
        request.setDeviceId("device-abc");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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

    // ===================== FORGOT PASSWORD =====================

    @Test
    void forgotPassword_returns200_whenRequestValid() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("jane@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authTokenService).requestPasswordReset("jane@example.com");
    }

    @Test
    void forgotPassword_returns400_whenEmailBlank() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===================== RESET PASSWORD =====================

    @Test
    void resetPassword_returns200_whenTokenValid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-reset-token");
        request.setNewPassword("newpassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authTokenService).resetPassword("valid-reset-token", "newpassword123");
    }

    @Test
    void resetPassword_returns400_whenTokenInvalid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("bad-token");
        request.setNewPassword("newpassword123");

        doThrow(new InvalidAuthTokenException("Invalid or expired token"))
                .when(authTokenService).resetPassword("bad-token", "newpassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_returns400_whenNewPasswordTooShort() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-reset-token");
        request.setNewPassword("short");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===================== CHANGE PASSWORD =====================

    @Test
    void changePassword_returns200_whenAuthorizedAndValid() throws Exception {
        stubValidAccessToken(1L);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldpassword");
        request.setNewPassword("newpassword123");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authTokenService).changePassword(1L, "oldpassword", "newpassword123");
    }

    @Test
    void changePassword_returns403_whenNoAuthorizationHeader() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldpassword");
        request.setNewPassword("newpassword123");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_returns401_whenCurrentPasswordIncorrect() throws Exception {
        stubValidAccessToken(1L);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongpassword");
        request.setNewPassword("newpassword123");

        doThrow(new InvalidCredentialsException("Current password is incorrect"))
                .when(authTokenService).changePassword(1L, "wrongpassword", "newpassword123");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ===================== VERIFY EMAIL =====================

    @Test
    void verifyEmail_returns200_whenTokenValid() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("valid-verify-token");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authTokenService).verifyEmail("valid-verify-token");
    }

    @Test
    void verifyEmail_returns400_whenTokenInvalid() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("bad-token");

        doThrow(new InvalidAuthTokenException("Invalid or expired token"))
                .when(authTokenService).verifyEmail("bad-token");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===================== RESEND VERIFICATION =====================

    @Test
    void resendVerification_returns200_whenAuthorized() throws Exception {
        stubValidAccessToken(1L);
        when(authTokenService.resendVerificationEmail(1L)).thenReturn("Verification email sent");

        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent"));
    }

    @Test
    void resendVerification_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email/resend"))
                .andExpect(status().isForbidden());
    }

    // ===================== VERIFY MOBILE =====================

    @Test
    void verifyMobile_returns200_whenCodeValid() throws Exception {
        stubValidAccessToken(1L);
        VerifyMobileRequest request = new VerifyMobileRequest();
        request.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/verify-mobile")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(mobileOtpService).verifyMobile(1L, "123456");
    }

    @Test
    void verifyMobile_returns400_whenCodeIncorrect() throws Exception {
        stubValidAccessToken(1L);
        VerifyMobileRequest request = new VerifyMobileRequest();
        request.setCode("999999");

        doThrow(new InvalidOtpException("Incorrect code"))
                .when(mobileOtpService).verifyMobile(1L, "999999");

        mockMvc.perform(post("/api/v1/auth/verify-mobile")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_OTP"));
    }

    @Test
    void verifyMobile_returns400_whenCodeIsNotSixDigits() throws Exception {
        stubValidAccessToken(1L);
        VerifyMobileRequest request = new VerifyMobileRequest();
        request.setCode("123");

        mockMvc.perform(post("/api/v1/auth/verify-mobile")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyMobile_returns403_whenNoAuthorizationHeader() throws Exception {
        VerifyMobileRequest request = new VerifyMobileRequest();
        request.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/verify-mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ===================== RESEND MOBILE VERIFICATION =====================

    @Test
    void resendMobileVerification_returns200_whenAuthorized() throws Exception {
        stubValidAccessToken(1L);
        when(mobileOtpService.resendOtp(1L)).thenReturn("Verification code sent");

        mockMvc.perform(post("/api/v1/auth/verify-mobile/resend")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification code sent"));
    }

    @Test
    void resendMobileVerification_returns400_whenNoMobileNumberOnFile() throws Exception {
        stubValidAccessToken(1L);
        when(mobileOtpService.resendOtp(1L))
                .thenThrow(new MobileNumberNotSetException("Add a mobile number to your profile first"));

        mockMvc.perform(post("/api/v1/auth/verify-mobile/resend")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MOBILE_NUMBER_NOT_SET"));
    }

    @Test
    void resendMobileVerification_returns403_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-mobile/resend"))
                .andExpect(status().isForbidden());
    }
}
