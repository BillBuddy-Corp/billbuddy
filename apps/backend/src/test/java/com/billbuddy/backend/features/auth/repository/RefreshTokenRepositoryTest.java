package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.RefreshToken;
import com.billbuddy.backend.features.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        User user = User.signupWithEmail(email, "hashed-password", "Jane Doe", null);
        return userRepository.save(user);
    }

    private RefreshToken persistToken(User user, String tokenHash, String deviceId, LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = RefreshToken.create(
                user, tokenHash, expiresAt, deviceId, "Test Device", "127.0.0.1", "JUnit"
        );
        if (revoked) {
            token.revoke();
        }
        return refreshTokenRepository.save(token);
    }

    @Test
    void revokeActiveByUserIdAndDeviceId_revokesOnlyTheMatchingDeviceSession() {
        User user = persistUser("jane@example.com");
        persistToken(user, "hash-device-a", "device-a", LocalDateTime.now().plusDays(10), false);
        persistToken(user, "hash-device-b", "device-b", LocalDateTime.now().plusDays(10), false);

        entityManager.flush();

        int updated = refreshTokenRepository.revokeActiveByUserIdAndDeviceId(user.getId(), "device-a");
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);

        RefreshToken deviceAToken = refreshTokenRepository.findByTokenHash("hash-device-a").orElseThrow();
        RefreshToken deviceBToken = refreshTokenRepository.findByTokenHash("hash-device-b").orElseThrow();

        assertThat(deviceAToken.isRevoked()).isTrue();
        assertThat(deviceBToken.isRevoked()).isFalse();
    }

    @Test
    void revokeAllByUserId_revokesEverySessionAcrossAllDevices() {
        User user = persistUser("jane@example.com");
        persistToken(user, "hash-device-a", "device-a", LocalDateTime.now().plusDays(10), false);
        persistToken(user, "hash-device-b", "device-b", LocalDateTime.now().plusDays(10), false);

        entityManager.flush();

        int updated = refreshTokenRepository.revokeAllByUserId(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);

        assertThat(refreshTokenRepository.findByTokenHash("hash-device-a").orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash("hash-device-b").orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void findActiveSessions_excludesRevokedAndExpiredSessions() {
        User user = persistUser("jane@example.com");
        persistToken(user, "hash-active", "device-active", LocalDateTime.now().plusDays(10), false);
        persistToken(user, "hash-revoked", "device-revoked", LocalDateTime.now().plusDays(10), true);
        persistToken(user, "hash-expired", "device-expired", LocalDateTime.now().minusDays(1), false);

        entityManager.flush();
        entityManager.clear();

        List<RefreshToken> activeSessions = refreshTokenRepository.findActiveSessions(user.getId());

        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).getTokenHash()).isEqualTo("hash-active");
    }

    @Test
    void deleteExpiredTokens_deletesOnlyRowsPastExpiry() {
        User user = persistUser("jane@example.com");
        persistToken(user, "hash-expired", "device-a", LocalDateTime.now().minusDays(1), false);
        persistToken(user, "hash-not-expired", "device-b", LocalDateTime.now().plusDays(10), false);

        entityManager.flush();

        int deleted = refreshTokenRepository.deleteExpiredTokens();
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);

        Optional<RefreshToken> remaining = refreshTokenRepository.findByTokenHash("hash-not-expired");
        assertThat(remaining).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("hash-expired")).isEmpty();
    }
}
