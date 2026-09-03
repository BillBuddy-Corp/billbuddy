package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.common.EmailService;
import com.billbuddy.backend.common.TokenGeneratorUtil;
import com.billbuddy.backend.exception.InvalidAuthTokenException;
import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.AuthToken;
import com.billbuddy.backend.features.auth.model.AuthTokenPurpose;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.AuthTokenRepository;
import com.billbuddy.backend.features.auth.repository.RefreshTokenRepository;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class AuthTokenService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth-token.password-reset-expiry-minutes}")
    private int passwordResetExpiryMinutes;

    @Value("${auth-token.email-verification-expiry-hours}")
    private int emailVerificationExpiryHours;

    @Value("${app.reset-password-base-url}")
    private String resetPasswordBaseUrl;

    @Value("${app.verify-email-base-url}")
    private String verifyEmailBaseUrl;

    public AuthTokenService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // ===================== PASSWORD RESET =====================

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // no enumeration leak -- silently no-op
            return;
        }
        User user = userOpt.get();

        authTokenRepository.revokeActiveByUserIdAndPurpose(user.getId(), AuthTokenPurpose.PASSWORD_RESET);

        String rawToken = TokenGeneratorUtil.generate();
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = AuthToken.create(
                user, AuthTokenPurpose.PASSWORD_RESET, tokenHash,
                LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes)
        );
        authTokenRepository.save(token);

        String resetLink = resetPasswordBaseUrl + "?token=" + rawToken;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        } catch (Exception ex) {
            // must not escape this transactional method -- an uncaught exception here would mark
            // the ambient transaction rollback-only even if a caller (e.g. signup) tries to catch it.
            log.warn("Failed to send password reset email, userId={}", user.getId(), ex);
        }

        log.info("Password reset requested, userId={}", user.getId());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AuthToken token = requireUsableToken(rawToken, AuthTokenPurpose.PASSWORD_RESET);
        User user = token.getUser();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        token.markUsed();

        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("Password reset successful, userId={}", user.getId());
    }

    // ===================== CHANGE PASSWORD =====================

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());

        refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Password changed, userId={}", userId);
    }

    // ===================== EMAIL VERIFICATION =====================

    @Transactional
    public void sendVerificationEmail(User user) {
        if (user.getEmailVerifiedAt() != null) {
            return;
        }

        authTokenRepository.revokeActiveByUserIdAndPurpose(user.getId(), AuthTokenPurpose.EMAIL_VERIFICATION);

        String rawToken = TokenGeneratorUtil.generate();
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = AuthToken.create(
                user, AuthTokenPurpose.EMAIL_VERIFICATION, tokenHash,
                LocalDateTime.now().plusHours(emailVerificationExpiryHours)
        );
        authTokenRepository.save(token);

        String verifyLink = verifyEmailBaseUrl + "?token=" + rawToken;
        try {
            emailService.sendVerificationEmail(user.getEmail(), verifyLink);
        } catch (Exception ex) {
            // see requestPasswordReset -- must not escape this transactional method
            log.warn("Failed to send verification email, userId={}", user.getId(), ex);
        }
    }

    @Transactional
    public String resendVerificationEmail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            return "Email already verified";
        }

        sendVerificationEmail(user);
        return "Verification email sent";
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = requireUsableToken(rawToken, AuthTokenPurpose.EMAIL_VERIFICATION);
        User user = token.getUser();

        user.setEmailVerifiedAt(LocalDateTime.now());
        token.markUsed();

        log.info("Email verified, userId={}", user.getId());
    }

    // ===================== HELPERS =====================

    private AuthToken requireUsableToken(String rawToken, AuthTokenPurpose purpose) {
        String tokenHash = TokenHashUtil.sha256(rawToken);
        AuthToken token = authTokenRepository.findByTokenHashAndPurpose(tokenHash, purpose)
                .orElseThrow(() -> new InvalidAuthTokenException("Invalid or expired token"));
        if (!token.isUsable()) {
            throw new InvalidAuthTokenException("Invalid or expired token");
        }
        return token;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
