package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.features.auth.model.MobileOtp;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.MobileOtpRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpAttemptServiceTest {

    @Mock
    private MobileOtpRepository mobileOtpRepository;

    @InjectMocks
    private OtpAttemptService otpAttemptService;

    @Test
    void consumeAttempt_decrementsAndSaves_whenOtpExists() {
        User user = User.signupWithEmail("jane@example.com", "hashed-password", "Jane", "+919876543210");
        MobileOtp otp = MobileOtp.create(user, TokenHashUtil.sha256("123456"), LocalDateTime.now().plusMinutes(10), 5);
        ReflectionTestUtils.setField(otp, "id", 100L);
        when(mobileOtpRepository.findById(100L)).thenReturn(Optional.of(otp));

        otpAttemptService.consumeAttempt(100L);

        assertThat(otp.getAttemptsRemaining()).isEqualTo(4);
        verify(mobileOtpRepository).save(otp);
    }

    @Test
    void consumeAttempt_noOps_whenOtpNoLongerExists() {
        when(mobileOtpRepository.findById(100L)).thenReturn(Optional.empty());

        otpAttemptService.consumeAttempt(100L);

        verify(mobileOtpRepository, never()).save(any());
    }
}
