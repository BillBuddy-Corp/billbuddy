package com.billbuddy.backend.features.auth.service;

import com.billbuddy.backend.exception.InvalidCredentialsException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Service
public class GoogleIdTokenVerifierService implements GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierService(@Value("${google.client-id}") String clientId) {
        try {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        } catch (GeneralSecurityException | java.io.IOException ex) {
            throw new IllegalStateException("Failed to initialize Google ID token verifier", ex);
        }
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception ex) {
            log.warn("Google ID token verification failed", ex);
            throw new InvalidCredentialsException("Invalid Google token");
        }

        if (token == null) {
            throw new InvalidCredentialsException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        String fullName = (String) payload.get("name");

        return new GoogleUserInfo(payload.getEmail(), emailVerified, fullName);
    }
}
