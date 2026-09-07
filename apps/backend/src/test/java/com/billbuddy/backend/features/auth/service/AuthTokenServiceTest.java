package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.common.EmailService;
import com.billbuddy.backend.exception.InvalidAuthTokenException;
import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.billbuddy.backend.exception.UserAlreadyExistsException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.AuthToken;
import com.billbuddy.backend.features.auth.model.AuthTokenPurpose;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.AuthTokenRepository;
import com.billbuddy.backend.features.auth.repository.RefreshTokenRepository;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authTokenService, "passwordResetExpiryMinutes", 60);
        ReflectionTestUtils.setField(authTokenService, "emailVerificationExpiryHours", 24);
        ReflectionTestUtils.setField(authTokenService, "resetPasswordBaseUrl", "http://localhost:3000/auth/reset-password");
        ReflectionTestUtils.setField(authTokenService, "verifyEmailBaseUrl", "http://localhost:3000/auth/verify-email");
        ReflectionTestUtils.setField(authTokenService, "emailChangeExpiryMinutes", 60);
        ReflectionTestUtils.setField(authTokenService, "emailChangeBaseUrl", "http://localhost:3000/auth/confirm-email-change");
    }

    private User buildUser(Long id, String email) {
        User user = User.signupWithEmail(email, "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private AuthToken buildToken(Long id, User user, AuthTokenPurpose purpose, String tokenHash, boolean revoked, LocalDateTime expiresAt, LocalDateTime usedAt) {
        AuthToken token = AuthToken.create(user, purpose, tokenHash, expiresAt);
        ReflectionTestUtils.setField(token, "id", id);
        if (revoked) {
            token.revoke();
        }
        if (usedAt != null) {
            ReflectionTestUtils.setField(token, "usedAt", usedAt);
        }
        return token;
    }

    // ===================== REQUEST PASSWORD RESET =====================

    @Test
    void requestPasswordReset_createsTokenAndSendsEmail_whenUserExists() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        authTokenService.requestPasswordReset("jane@example.com");

        verify(authTokenRepository).revokeActiveByUserIdAndPurpose(1L, AuthTokenPurpose.PASSWORD_RESET);

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPurpose()).isEqualTo(AuthTokenPurpose.PASSWORD_RESET);
        assertThat(tokenCaptor.getValue().getUser()).isEqualTo(user);

        verify(emailService).sendPasswordResetEmail(eq("jane@example.com"), anyString());
    }

    @Test
    void requestPasswordReset_normalizesEmailCaseAndWhitespace() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        authTokenService.requestPasswordReset("  Jane@Example.com  ");

        verify(userRepository).findByEmail("jane@example.com");
    }

    @Test
    void requestPasswordReset_silentlyNoOps_whenEmailDoesNotExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authTokenService.requestPasswordReset("nobody@example.com");

        verify(authTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    // ===================== RESET PASSWORD =====================

    @Test
    void resetPassword_updatesPasswordAndRevokesSessions_whenTokenIsUsable() {
        User user = buildUser(1L, "jane@example.com");
        String rawToken = "raw-reset-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(100L, user, AuthTokenPurpose.PASSWORD_RESET, tokenHash, false, LocalDateTime.now().plusMinutes(30), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword123")).thenReturn("hashed-new-password");

        authTokenService.resetPassword(rawToken, "newpassword123");

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new-password");
        assertThat(user.getPasswordUpdatedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void resetPassword_throwsInvalidAuthToken_whenTokenNotFound() {
        when(authTokenRepository.findByTokenHashAndPurpose(anyString(), eq(AuthTokenPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.resetPassword("nonexistent-token", "newpassword123"))
                .isInstanceOf(InvalidAuthTokenException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void resetPassword_throwsInvalidAuthToken_whenTokenRevoked() {
        User user = buildUser(1L, "jane@example.com");
        String rawToken = "raw-reset-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(100L, user, AuthTokenPurpose.PASSWORD_RESET, tokenHash, true, LocalDateTime.now().plusMinutes(30), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authTokenService.resetPassword(rawToken, "newpassword123"))
                .isInstanceOf(InvalidAuthTokenException.class);
    }

    @Test
    void resetPassword_throwsInvalidAuthToken_whenTokenExpired() {
        User user = buildUser(1L, "jane@example.com");
        String rawToken = "raw-reset-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(100L, user, AuthTokenPurpose.PASSWORD_RESET, tokenHash, false, LocalDateTime.now().minusMinutes(1), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authTokenService.resetPassword(rawToken, "newpassword123"))
                .isInstanceOf(InvalidAuthTokenException.class);
    }

    @Test
    void resetPassword_throwsInvalidAuthToken_whenTokenAlreadyUsed() {
        User user = buildUser(1L, "jane@example.com");
        String rawToken = "raw-reset-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(100L, user, AuthTokenPurpose.PASSWORD_RESET, tokenHash, false, LocalDateTime.now().plusMinutes(30), LocalDateTime.now().minusMinutes(5));

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authTokenService.resetPassword(rawToken, "newpassword123"))
                .isInstanceOf(InvalidAuthTokenException.class);
    }

    // ===================== CHANGE PASSWORD =====================

    @Test
    void changePassword_updatesPasswordAndRevokesSessions_whenCurrentPasswordCorrect() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpassword", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newpassword123")).thenReturn("hashed-new-password");

        authTokenService.changePassword(1L, "oldpassword", "newpassword123");

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new-password");
        assertThat(user.getPasswordUpdatedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void changePassword_throwsInvalidCredentials_whenCurrentPasswordWrong() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authTokenService.changePassword(1L, "wrongpassword", "newpassword123"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void changePassword_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.changePassword(1L, "oldpassword", "newpassword123"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== EMAIL VERIFICATION =====================

    @Test
    void sendVerificationEmail_createsTokenAndSendsEmail_whenNotVerified() {
        User user = buildUser(1L, "jane@example.com");

        authTokenService.sendVerificationEmail(user);

        verify(authTokenRepository).revokeActiveByUserIdAndPurpose(1L, AuthTokenPurpose.EMAIL_VERIFICATION);
        verify(authTokenRepository).save(any(AuthToken.class));
        verify(emailService).sendVerificationEmail(eq("jane@example.com"), anyString());
    }

    @Test
    void sendVerificationEmail_noOps_whenAlreadyVerified() {
        User user = buildUser(1L, "jane@example.com");
        user.setEmailVerifiedAt(LocalDateTime.now());

        authTokenService.sendVerificationEmail(user);

        verify(authTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerificationEmail_returnsAlreadyVerifiedMessage_withoutSendingEmail() {
        User user = buildUser(1L, "jane@example.com");
        user.setEmailVerifiedAt(LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String message = authTokenService.resendVerificationEmail(1L);

        assertThat(message).isEqualTo("Email already verified");
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerificationEmail_sendsEmail_whenNotYetVerified() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String message = authTokenService.resendVerificationEmail(1L);

        assertThat(message).isEqualTo("Verification email sent");
        verify(emailService).sendVerificationEmail(eq("jane@example.com"), anyString());
    }

    @Test
    void resendVerificationEmail_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.resendVerificationEmail(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void verifyEmail_setsEmailVerifiedAt_whenTokenIsUsable() {
        User user = buildUser(1L, "jane@example.com");
        String rawToken = "raw-verify-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(200L, user, AuthTokenPurpose.EMAIL_VERIFICATION, tokenHash, false, LocalDateTime.now().plusHours(24), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        authTokenService.verifyEmail(rawToken);

        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void verifyEmail_throwsInvalidAuthToken_whenTokenNotFoundForThatPurpose() {
        // e.g. a PASSWORD_RESET token's hash won't match an EMAIL_VERIFICATION lookup
        when(authTokenRepository.findByTokenHashAndPurpose(anyString(), eq(AuthTokenPurpose.EMAIL_VERIFICATION)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.verifyEmail("some-token"))
                .isInstanceOf(InvalidAuthTokenException.class);
    }

    // ===================== REQUEST EMAIL CHANGE =====================

    @Test
    void requestEmailChange_createsTokenAndSendsEmailToNewAddress_whenPasswordCorrect() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpassword", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);

        authTokenService.requestEmailChange(1L, "newemail@example.com", "correctpassword");

        assertThat(user.getPendingEmail()).isEqualTo("newemail@example.com");
        verify(authTokenRepository).revokeActiveByUserIdAndPurpose(1L, AuthTokenPurpose.EMAIL_CHANGE);

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPurpose()).isEqualTo(AuthTokenPurpose.EMAIL_CHANGE);

        verify(emailService).sendEmailChangeConfirmationEmail(eq("newemail@example.com"), anyString());
    }

    @Test
    void requestEmailChange_normalizesNewEmailCaseAndWhitespace() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpassword", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);

        authTokenService.requestEmailChange(1L, "  NewEmail@Example.com  ", "correctpassword");

        assertThat(user.getPendingEmail()).isEqualTo("newemail@example.com");
    }

    @Test
    void requestEmailChange_throwsInvalidCredentials_whenPasswordIncorrect() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authTokenService.requestEmailChange(1L, "newemail@example.com", "wrongpassword"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(authTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void requestEmailChange_throwsUserAlreadyExists_whenNewEmailAlreadyRegistered() {
        User user = buildUser(1L, "jane@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpassword", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authTokenService.requestEmailChange(1L, "taken@example.com", "correctpassword"))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(user.getPendingEmail()).isNull();
        verify(authTokenRepository, never()).save(any());
    }

    @Test
    void requestEmailChange_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.requestEmailChange(1L, "newemail@example.com", "correctpassword"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== CONFIRM EMAIL CHANGE =====================

    @Test
    void confirmEmailChange_updatesEmailVerifiesAndRevokesSessions_whenTokenIsUsable() {
        User user = buildUser(1L, "jane@example.com");
        user.requestEmailChange("newemail@example.com");
        String rawToken = "raw-email-change-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(300L, user, AuthTokenPurpose.EMAIL_CHANGE, tokenHash, false, LocalDateTime.now().plusMinutes(60), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.EMAIL_CHANGE))
                .thenReturn(Optional.of(token));
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);

        authTokenService.confirmEmailChange(rawToken);

        assertThat(user.getEmail()).isEqualTo("newemail@example.com");
        assertThat(user.getPendingEmail()).isNull();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void confirmEmailChange_throwsInvalidAuthToken_whenTokenNotUsable() {
        when(authTokenRepository.findByTokenHashAndPurpose(anyString(), eq(AuthTokenPurpose.EMAIL_CHANGE)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.confirmEmailChange("nonexistent-token"))
                .isInstanceOf(InvalidAuthTokenException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void confirmEmailChange_throwsUserAlreadyExists_whenPendingEmailTakenSinceRequest() {
        User user = buildUser(1L, "jane@example.com");
        user.requestEmailChange("newemail@example.com");
        String rawToken = "raw-email-change-token";
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = buildToken(300L, user, AuthTokenPurpose.EMAIL_CHANGE, tokenHash, false, LocalDateTime.now().plusMinutes(60), null);

        when(authTokenRepository.findByTokenHashAndPurpose(tokenHash, AuthTokenPurpose.EMAIL_CHANGE))
                .thenReturn(Optional.of(token));
        when(userRepository.existsByEmail("newemail@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authTokenService.confirmEmailChange(rawToken))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(user.getEmail()).isEqualTo("jane@example.com");
        assertThat(token.getUsedAt()).isNull();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }
}
