package com.billbuddy.backend.common;

// Swappable, same pattern as FileStorageService: a real provider (Twilio, MSG91, ...) is a new
// implementation class and a config change, not a rewrite of MobileOtpService.
public interface SmsService {

    void sendOtp(String mobileNumber, String code);
}
