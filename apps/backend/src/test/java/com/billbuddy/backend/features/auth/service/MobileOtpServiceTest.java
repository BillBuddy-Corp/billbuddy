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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class MobileOtpServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MobileOtpRepository mobileOtpRepository;

    @Mock
    private SmsService smsService;

    @Mock
    private OtpAttemptService otpAttemptService;

    @InjectMocks
    private MobileOtpService mobileOtpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mobileOtpService, "expiryMinutes", 10);
        ReflectionTestUtils.setField(mobileOtpService, "maxAttempts", 5);
    }

    private User buildUser(Long id, String mobileNumber) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, mobileNumber);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private MobileOtp buildOtp(Long id, User user, String code, boolean revoked, int attemptsRemaining, LocalDateTime expiresAt, LocalDateTime usedAt) {
        MobileOtp otp = MobileOtp.create(user, TokenHashUtil.sha256(code), expiresAt, attemptsRemaining);
        ReflectionTestUtils.setField(otp, "id", id);
        if (revoked) {
            otp.revoke();
        }
        if (usedAt != null) {
            ReflectionTestUtils.setField(otp, "usedAt", usedAt);
        }
        return otp;
    }

    // ===================== SEND ON SIGNUP (sendOtpIfMobileNumberPresent) =====================

    @Test
    void sendOtpIfMobileNumberPresent_createsOtpAndTextsIt_whenMobileNumberGiven() {
        User user = buildUser(1L, "+919876543210");

        mobileOtpService.sendOtpIfMobileNumberPresent(user);

        verify(mobileOtpRepository).revokeActiveByUserId(1L);
        ArgumentCaptor<MobileOtp> otpCaptor = ArgumentCaptor.forClass(MobileOtp.class);
        verify(mobileOtpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getUser()).isEqualTo(user);
        assertThat(otpCaptor.getValue().getAttemptsRemaining()).isEqualTo(5);

        verify(smsService).sendOtp(eq("+919876543210"), anyString());
    }

    @Test
    void sendOtpIfMobileNumberPresent_noOps_whenNoMobileNumber() {
        User user = buildUser(1L, null);

        mobileOtpService.sendOtpIfMobileNumberPresent(user);

        verifyNoInteractions(mobileOtpRepository, smsService);
    }

    @Test
    void sendOtpIfMobileNumberPresent_noOps_whenAlreadyVerified() {
        User user = buildUser(1L, "+919876543210");
        user.setMobileVerifiedAt(LocalDateTime.now());

        mobileOtpService.sendOtpIfMobileNumberPresent(user);

        verifyNoInteractions(mobileOtpRepository, smsService);
    }

    @Test
    void sendOtpIfMobileNumberPresent_doesNotPropagate_whenSmsSendFails() {
        // must not escape -- a caller like signup must not have its own transaction poisoned
        User user = buildUser(1L, "+919876543210");
        doThrow(new RuntimeException("provider down")).when(smsService).sendOtp(anyString(), anyString());

        mobileOtpService.sendOtpIfMobileNumberPresent(user);

        verify(mobileOtpRepository).save(any(MobileOtp.class));
    }

    // ===================== RESEND =====================

    @Test
    void resendOtp_sendsCodeAndReturnsMessage_whenNotYetVerified() {
        User user = buildUser(1L, "+919876543210");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String message = mobileOtpService.resendOtp(1L);

        assertThat(message).isEqualTo("Verification code sent");
        verify(smsService).sendOtp(eq("+919876543210"), anyString());
    }

    @Test
    void resendOtp_returnsAlreadyVerifiedMessage_withoutSendingSms() {
        User user = buildUser(1L, "+919876543210");
        user.setMobileVerifiedAt(LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String message = mobileOtpService.resendOtp(1L);

        assertThat(message).isEqualTo("Mobile number already verified");
        verifyNoInteractions(smsService);
    }

    @Test
    void resendOtp_throwsMobileNumberNotSet_whenNoNumberOnFile() {
        User user = buildUser(1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> mobileOtpService.resendOtp(1L))
                .isInstanceOf(MobileNumberNotSetException.class);

        verifyNoInteractions(smsService);
    }

    @Test
    void resendOtp_throwsUserNotFound_whenMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mobileOtpService.resendOtp(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ===================== VERIFY =====================

    @Test
    void verifyMobile_setsMobileVerifiedAt_whenCodeIsCorrect() {
        User user = buildUser(1L, "+919876543210");
        MobileOtp otp = buildOtp(100L, user, "123456", false, 5, LocalDateTime.now().plusMinutes(10), null);
        when(mobileOtpRepository.findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(otp));

        mobileOtpService.verifyMobile(1L, "123456");

        assertThat(user.getMobileVerifiedAt()).isNotNull();
        assertThat(otp.getUsedAt()).isNotNull();
    }

    @Test
    void verifyMobile_throwsInvalidOtpAndConsumesAttempt_whenCodeIsWrong() {
        User user = buildUser(1L, "+919876543210");
        MobileOtp otp = buildOtp(100L, user, "123456", false, 5, LocalDateTime.now().plusMinutes(10), null);
        when(mobileOtpRepository.findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> mobileOtpService.verifyMobile(1L, "999999"))
                .isInstanceOf(InvalidOtpException.class);

        // the decrement itself is a REQUIRES_NEW call on OtpAttemptService, not a mutation of
        // this in-memory entity -- so it's what gets verified here, not otp's own state
        verify(otpAttemptService).consumeAttempt(100L);
        assertThat(user.getMobileVerifiedAt()).isNull();
    }

    @Test
    void verifyMobile_throwsInvalidOtp_whenNoOtpPending() {
        when(mobileOtpRepository.findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mobileOtpService.verifyMobile(1L, "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyMobile_throwsInvalidOtp_whenOtpExpired() {
        User user = buildUser(1L, "+919876543210");
        MobileOtp otp = buildOtp(100L, user, "123456", false, 5, LocalDateTime.now().minusMinutes(1), null);
        when(mobileOtpRepository.findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> mobileOtpService.verifyMobile(1L, "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyMobile_throwsInvalidOtp_whenAttemptsExhausted() {
        User user = buildUser(1L, "+919876543210");
        MobileOtp otp = buildOtp(100L, user, "123456", false, 0, LocalDateTime.now().plusMinutes(10), null);
        when(mobileOtpRepository.findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(otp));

        // even the correct code is rejected once attempts run out
        assertThatThrownBy(() -> mobileOtpService.verifyMobile(1L, "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }
}
