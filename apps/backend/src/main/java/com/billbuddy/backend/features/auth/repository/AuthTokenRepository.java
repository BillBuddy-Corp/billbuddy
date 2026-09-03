package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.AuthToken;
import com.billbuddy.backend.features.auth.model.AuthTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    @Modifying
    @Query("""
        update AuthToken t
        set t.revoked = true
        where t.user.id = :userId
          and t.purpose = :purpose
          and t.revoked = false
          and t.usedAt is null
    """)
    int revokeActiveByUserIdAndPurpose(Long userId, AuthTokenPurpose purpose);
}
