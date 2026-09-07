package com.billbuddy.backend.features.auth.service;

// Swappable, same pattern as SmsService/PushNotificationService: verification behind an
// interface so AuthServiceTest can mock it entirely, without needing a real Google-signed token.
public interface GoogleTokenVerifier {

    GoogleUserInfo verify(String idToken);

    record GoogleUserInfo(String email, boolean emailVerified, String fullName) {
    }
}
