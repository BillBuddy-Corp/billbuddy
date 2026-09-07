package com.billbuddy.backend.features.auth.model;

import com.billbuddy.backend.features.storage.model.StoredFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

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

    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @Column(name = "password_hash")
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_pic_file_id")
    private StoredFile profilePicFile;

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
            String defaultCurrency,
            LocalDateTime emailVerifiedAt
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.mobileNumber = mobileNumber;
        this.authProvider = authProvider;
        this.defaultCurrency = defaultCurrency;
        this.emailVerifiedAt = emailVerifiedAt;
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

    public static User signupWithGoogle(String email, String fullName) {
        return User.builder()
                .email(email)
                .fullName(fullName)
                .authProvider(AuthProvider.GOOGLE)
                .defaultCurrency("INR")
                .emailVerifiedAt(LocalDateTime.now())
                .build();
    }

    public void updateProfile(String fullName, StoredFile profilePicFile, String defaultCurrency, String mobileNumber, String fcmToken) {
        this.fullName = fullName;
        this.profilePicFile = profilePicFile;
        this.defaultCurrency = defaultCurrency;
        if (!Objects.equals(this.mobileNumber, mobileNumber)) {
            this.mobileNumber = mobileNumber;
            this.mobileVerifiedAt = null;
        }
        // preserved when omitted -- unlike the other fields here, this isn't something a human
        // edits in a profile form, it's refreshed independently by the client's own push SDK, so
        // a routine name/currency update must not silently wipe an already-registered device
        if (fcmToken != null) {
            this.fcmToken = fcmToken;
        }
    }

    public void requestEmailChange(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    public void confirmEmailChange() {
        this.email = this.pendingEmail;
        this.pendingEmail = null;
        this.emailVerifiedAt = LocalDateTime.now();
    }

}
