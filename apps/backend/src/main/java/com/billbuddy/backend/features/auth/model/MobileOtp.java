package com.billbuddy.backend.features.auth.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Deliberately separate from AuthToken: a 6-digit code is guessable within a short window in a
// way a long random token isn't, so this needs an attempt counter AuthToken has no use for, and
// a much shorter expiry than the hours-long email-verification/password-reset tokens.
@Entity
@Table(name = "mobile_otps")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MobileOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // always a SHA-256 hash -- the raw code is only ever texted, never persisted.
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static MobileOtp create(User user, String codeHash, LocalDateTime expiresAt, int maxAttempts) {
        MobileOtp otp = new MobileOtp();
        otp.user = user;
        otp.codeHash = codeHash;
        otp.expiresAt = expiresAt;
        otp.attemptsRemaining = maxAttempts;
        return otp;
    }

    public void revoke() {
        this.revoked = true;
    }

    public void markUsed() {
        this.usedAt = LocalDateTime.now();
    }

    public void consumeAttempt() {
        this.attemptsRemaining = Math.max(0, this.attemptsRemaining - 1);
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        return !this.revoked && this.usedAt == null && !isExpired() && this.attemptsRemaining > 0;
    }
}
