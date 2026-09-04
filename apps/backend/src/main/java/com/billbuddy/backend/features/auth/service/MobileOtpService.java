package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.common.SmsService;
import com.billbuddy.backend.exception.InvalidOtpException;
import com.billbuddy.backend.exception.MobileNumberNotSetException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.MobileOtp;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.MobileOtpRepository;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
public class MobileOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MobileOtpRepository mobileOtpRepository;
    private final SmsService smsService;
    private final OtpAttemptService otpAttemptService;

    @Value("${otp.expiry-minutes}")
    private int expiryMinutes;

    @Value("${otp.max-attempts}")
    private int maxAttempts;

    public MobileOtpService(
            UserRepository userRepository,
            MobileOtpRepository mobileOtpRepository,
            SmsService smsService,
            OtpAttemptService otpAttemptService
    ) {
        this.userRepository = userRepository;
        this.mobileOtpRepository = mobileOtpRepository;
        this.smsService = smsService;
        this.otpAttemptService = otpAttemptService;
    }

    // Called right after signup, same as sendVerificationEmail -- a no-op rather than an error
    // when no mobile number was given, since that's a perfectly normal signup.
    @Transactional
    public void sendOtpIfMobileNumberPresent(User user) {
        if (user.getMobileNumber() == null || user.getMobileVerifiedAt() != null) {
            return;
        }
        issueAndSendOtp(user);
    }

    @Transactional
    public String resendOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getMobileNumber() == null) {
            throw new MobileNumberNotSetException("Add a mobile number to your profile first");
        }
        if (user.getMobileVerifiedAt() != null) {
            return "Mobile number already verified";
        }

        issueAndSendOtp(user);
        return "Verification code sent";
    }

    @Transactional
    public void verifyMobile(Long userId, String rawCode) {
        MobileOtp otp = mobileOtpRepository
                .findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired code"));

        if (!otp.isUsable()) {
            throw new InvalidOtpException("Invalid or expired code");
        }

        if (!otp.getCodeHash().equals(TokenHashUtil.sha256(rawCode))) {
            // committed in its own transaction -- see OtpAttemptService's javadoc
            otpAttemptService.consumeAttempt(otp.getId());
            throw new InvalidOtpException("Incorrect code");
        }

        otp.markUsed();
        User user = otp.getUser();
        user.setMobileVerifiedAt(LocalDateTime.now());

        log.info("Mobile verified, userId={}", user.getId());
    }

    // ===================== HELPERS =====================

    private void issueAndSendOtp(User user) {
        mobileOtpRepository.revokeActiveByUserId(user.getId());

        String code = generateCode();
        String codeHash = TokenHashUtil.sha256(code);
        MobileOtp otp = MobileOtp.create(user, codeHash, LocalDateTime.now().plusMinutes(expiryMinutes), maxAttempts);
        mobileOtpRepository.save(otp);

        try {
            smsService.sendOtp(user.getMobileNumber(), code);
        } catch (Exception ex) {
            // must not escape this transactional method -- an uncaught exception here would mark
            // the ambient transaction rollback-only even if a caller (e.g. signup) tries to catch it.
            log.warn("Failed to send OTP SMS, userId={}", user.getId(), ex);
        }

        log.info("Mobile OTP requested, userId={}", user.getId());
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
