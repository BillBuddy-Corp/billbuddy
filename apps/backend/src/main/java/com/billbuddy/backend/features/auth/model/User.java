package com.billbuddy.backend.features.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "profile_pic_url")
    private String profilePicUrl;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "default_currency")
    private String defaultCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    private AuthProvider authProvider;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "mobile_verified_at")
    private LocalDateTime mobileVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private User(
            String email,
            String passwordHash,
            String fullName,
            String mobileNumber,
            AuthProvider authProvider,
            String defaultCurrency
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.mobileNumber = mobileNumber;
        this.authProvider = authProvider;
        this.defaultCurrency = defaultCurrency;
    }

    public static User signupWithEmail(
            String email,
            String passwordHash,
            String fullName,
            String mobileNumber
    ) {
        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .mobileNumber(mobileNumber)
                .authProvider(AuthProvider.EMAIL)
                .defaultCurrency("INR")
                .build();
    }

}
