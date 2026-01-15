package com.billbuddy.backend.features.auth.repository;

import com.billbuddy.backend.features.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 🔐 Single token lookup
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndDeviceId(
            String tokenHash,
            String deviceId
    );

    // 📱 One active session per device
    @Modifying
    @Query("""
        update RefreshToken rt
        set rt.revoked = true
        where rt.user.id = :userId
          and rt.deviceId = :deviceId
          and rt.revoked = false
    """)
    int revokeActiveByUserIdAndDeviceId(Long userId, String deviceId);

    // 🚪 Logout all devices
    @Modifying
    @Query("""
        update RefreshToken rt
        set rt.revoked = true
        where rt.user.id = :userId
          and rt.revoked = false
    """)
    int revokeAllByUserId(Long userId);

    // 📋 Session listing
    List<RefreshToken> findAllByUser_IdAndRevokedFalse(Long userId);

    @Query("""
    select rt from RefreshToken rt
    where rt.user.id = :userId
      and rt.revoked = false
      and rt.expiresAt > CURRENT_TIMESTAMP
    order by rt.createdAt desc
""")
    List<RefreshToken> findActiveSessions(Long userId);

}
