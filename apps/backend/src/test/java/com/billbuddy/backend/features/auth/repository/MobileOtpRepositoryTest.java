package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.MobileOtp;
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
class MobileOtpRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MobileOtpRepository mobileOtpRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", "+919876543210"));
    }

    @Test
    void findFirstByUser_IdAndRevokedFalseAndUsedAtIsNull_returnsThePendingOtp() {
        User user = persistUser("jane@example.com");
        MobileOtp otp = mobileOtpRepository.save(MobileOtp.create(user, "a-hash", LocalDateTime.now().plusMinutes(10), 5));
        entityManager.flush();
        entityManager.clear();

        Optional<MobileOtp> result = mobileOtpRepository
                .findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(otp.getId());
    }

    @Test
    void findFirstByUser_IdAndRevokedFalseAndUsedAtIsNull_findsThePendingOne_amongRevokedAndUsedOnes() {
        // proves the active OTP is found regardless of how many stale ones exist alongside it,
        // without depending on fine-grained creation-timestamp ordering between rapid inserts
        User user = persistUser("jane@example.com");
        MobileOtp revoked = mobileOtpRepository.save(MobileOtp.create(user, "hash-a", LocalDateTime.now().plusMinutes(10), 5));
        revoked.revoke();
        mobileOtpRepository.save(revoked);
        MobileOtp used = mobileOtpRepository.save(MobileOtp.create(user, "hash-b", LocalDateTime.now().plusMinutes(10), 5));
        used.markUsed();
        mobileOtpRepository.save(used);
        MobileOtp pending = mobileOtpRepository.save(MobileOtp.create(user, "hash-c", LocalDateTime.now().plusMinutes(10), 5));
        entityManager.flush();
        entityManager.clear();

        Optional<MobileOtp> result = mobileOtpRepository
                .findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(pending.getId());
    }

    @Test
    void findFirstByUser_IdAndRevokedFalseAndUsedAtIsNull_returnsEmpty_whenOnlyRevokedOrUsedOtpsExist() {
        User user = persistUser("jane@example.com");
        MobileOtp revoked = mobileOtpRepository.save(MobileOtp.create(user, "hash-a", LocalDateTime.now().plusMinutes(10), 5));
        revoked.revoke();
        mobileOtpRepository.save(revoked);
        MobileOtp used = mobileOtpRepository.save(MobileOtp.create(user, "hash-b", LocalDateTime.now().plusMinutes(10), 5));
        used.markUsed();
        mobileOtpRepository.save(used);
        entityManager.flush();
        entityManager.clear();

        Optional<MobileOtp> result = mobileOtpRepository
                .findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void revokeActiveByUserId_revokesOnlyActiveOtps() {
        User user = persistUser("jane@example.com");
        MobileOtp active = mobileOtpRepository.save(MobileOtp.create(user, "hash-a", LocalDateTime.now().plusMinutes(10), 5));
        MobileOtp alreadyUsed = mobileOtpRepository.save(MobileOtp.create(user, "hash-b", LocalDateTime.now().plusMinutes(10), 5));
        alreadyUsed.markUsed();
        mobileOtpRepository.save(alreadyUsed);
        entityManager.flush();
        entityManager.clear();

        int revokedCount = mobileOtpRepository.revokeActiveByUserId(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(revokedCount).isEqualTo(1);
        assertThat(mobileOtpRepository.findById(active.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(mobileOtpRepository.findById(alreadyUsed.getId()).orElseThrow().isRevoked()).isFalse();
    }
}
