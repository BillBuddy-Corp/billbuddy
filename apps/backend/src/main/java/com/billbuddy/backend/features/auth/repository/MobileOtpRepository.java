package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.MobileOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MobileOtpRepository extends JpaRepository<MobileOtp, Long> {

    // A submitted 6-digit code isn't unique enough to look up by hash alone across all users
    // (unlike AuthToken's long random tokens) -- the caller is already authenticated, so this
    // scopes to their own current pending OTP, and the service compares hashes against that
    // specific record.
    Optional<MobileOtp> findFirstByUser_IdAndRevokedFalseAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("""
        update MobileOtp o
        set o.revoked = true
        where o.user.id = :userId
          and o.revoked = false
          and o.usedAt is null
    """)
    int revokeActiveByUserId(Long userId);
}
