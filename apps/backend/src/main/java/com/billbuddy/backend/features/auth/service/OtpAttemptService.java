package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.features.auth.repository.MobileOtpRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

// A dedicated bean purely so REQUIRES_NEW is a genuine cross-bean call through Spring's real
// transactional proxy. verifyMobile always throws right after consuming a failed attempt, which
// would roll back the whole method (and the decrement with it, since it's an unchecked
// exception) if this lived in the same transaction -- and a same-class REQUIRES_NEW method on
// MobileOtpService wouldn't help either, since a plain internal method call bypasses the proxy
// that transactional advice depends on.
@Service
public class OtpAttemptService {

    private final MobileOtpRepository mobileOtpRepository;

    public OtpAttemptService(MobileOtpRepository mobileOtpRepository) {
        this.mobileOtpRepository = mobileOtpRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void consumeAttempt(Long otpId) {
        mobileOtpRepository.findById(otpId).ifPresent(otp -> {
            otp.consumeAttempt();
            mobileOtpRepository.save(otp);
        });
    }
}
