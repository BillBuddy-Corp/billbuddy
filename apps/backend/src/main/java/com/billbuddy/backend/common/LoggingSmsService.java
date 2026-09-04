package com.billbuddy.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Placeholder until a real provider (Twilio, MSG91, ...) is wired in -- logs the code instead of
// texting it, so the OTP flow is fully testable locally without any SMS account or cost. Not
// safe for production as-is: a real implementation must not log the raw code.
@Slf4j
@Service
public class LoggingSmsService implements SmsService {

    @Override
    public void sendOtp(String mobileNumber, String code) {
        log.info("SMS OTP (no provider configured, logging instead): to={}, code={}", mobileNumber, code);
    }
}
