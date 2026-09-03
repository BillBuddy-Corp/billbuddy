package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.AuthToken;
import com.billbuddy.backend.features.auth.model.AuthTokenPurpose;
import com.billbuddy.backend.features.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private AuthToken persistToken(User user, AuthTokenPurpose purpose, String tokenHash) {
        return authTokenRepository.save(AuthToken.create(user, purpose, tokenHash, LocalDateTime.now().plusHours(1)));
    }

    @Test
    void findByTokenHashAndPurpose_returnsTheToken_whenItMatchesBoth() {
        User user = persistUser("jane@example.com");
        persistToken(user, AuthTokenPurpose.PASSWORD_RESET, "hash-1");
        entityManager.flush();
        entityManager.clear();

        Optional<AuthToken> result = authTokenRepository.findByTokenHashAndPurpose("hash-1", AuthTokenPurpose.PASSWORD_RESET);

        assertThat(result).isPresent();
    }

    @Test
    void findByTokenHashAndPurpose_returnsEmpty_whenHashMatchesButPurposeDoesNot() {
        User user = persistUser("jane@example.com");
        persistToken(user, AuthTokenPurpose.PASSWORD_RESET, "hash-1");
        entityManager.flush();
        entityManager.clear();

        Optional<AuthToken> result = authTokenRepository.findByTokenHashAndPurpose("hash-1", AuthTokenPurpose.EMAIL_VERIFICATION);

        assertThat(result).isEmpty();
    }

    @Test
    void revokeActiveByUserIdAndPurpose_revokesOnlyMatchingActiveTokens() {
        User user = persistUser("jane@example.com");
        AuthToken resetToken = persistToken(user, AuthTokenPurpose.PASSWORD_RESET, "reset-hash");
        AuthToken verifyToken = persistToken(user, AuthTokenPurpose.EMAIL_VERIFICATION, "verify-hash");
        entityManager.flush();

        int revokedCount = authTokenRepository.revokeActiveByUserIdAndPurpose(user.getId(), AuthTokenPurpose.PASSWORD_RESET);
        entityManager.flush();
        entityManager.clear();

        assertThat(revokedCount).isEqualTo(1);
        assertThat(authTokenRepository.findById(resetToken.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(authTokenRepository.findById(verifyToken.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeActiveByUserIdAndPurpose_doesNotRevokeAlreadyUsedTokens() {
        User user = persistUser("jane@example.com");
        AuthToken token = persistToken(user, AuthTokenPurpose.PASSWORD_RESET, "used-hash");
        token.markUsed();
        authTokenRepository.save(token);
        entityManager.flush();

        int revokedCount = authTokenRepository.revokeActiveByUserIdAndPurpose(user.getId(), AuthTokenPurpose.PASSWORD_RESET);

        assertThat(revokedCount).isZero();
    }

    @Test
    void revokeActiveByUserIdAndPurpose_onlyAffectsThatUsersTokens() {
        User jane = persistUser("jane@example.com");
        User bob = persistUser("bob@example.com");
        AuthToken janeToken = persistToken(jane, AuthTokenPurpose.PASSWORD_RESET, "jane-hash");
        AuthToken bobToken = persistToken(bob, AuthTokenPurpose.PASSWORD_RESET, "bob-hash");
        entityManager.flush();

        authTokenRepository.revokeActiveByUserIdAndPurpose(jane.getId(), AuthTokenPurpose.PASSWORD_RESET);
        entityManager.flush();
        entityManager.clear();

        assertThat(authTokenRepository.findById(janeToken.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(authTokenRepository.findById(bobToken.getId()).orElseThrow().isRevoked()).isFalse();
    }
}
