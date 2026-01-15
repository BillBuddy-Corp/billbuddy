package com.billbuddy.backend.features.auth.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static RefreshToken create(
            User user,
            String tokenHash,
            LocalDateTime expiresAt,
            String deviceId,
            String deviceName,
            String ipAddress,
            String userAgent
    ) {
        RefreshToken rt = new RefreshToken();
        rt.user = user;
        rt.tokenHash = tokenHash;
        rt.expiresAt = expiresAt;
        rt.revoked = false;
        rt.deviceId = deviceId;
        rt.deviceName = deviceName;
        rt.ipAddress = ipAddress;
        rt.userAgent = userAgent;
        rt.lastUsedAt = LocalDateTime.now();
        return rt;
    }

    public void revoke() {
        this.revoked = true;
    }
}
