package com.billbuddy.backend.features.groups.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_invites")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupInviteType type;

    @Column
    private String email;

    // EMAIL rows store a SHA-256 hash here (never re-shown after creation).
    // LINK rows store the raw token (only way to redisplay a shareable link).
    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static GroupInvite createEmailInvite(
            Group group,
            String email,
            String tokenHash,
            User invitedBy,
            LocalDateTime expiresAt
    ) {
        GroupInvite invite = new GroupInvite();
        invite.group = group;
        invite.type = GroupInviteType.EMAIL;
        invite.email = email;
        invite.token = tokenHash;
        invite.invitedBy = invitedBy;
        invite.expiresAt = expiresAt;
        return invite;
    }

    public static GroupInvite createLinkInvite(Group group, String rawToken, User invitedBy) {
        GroupInvite invite = new GroupInvite();
        invite.group = group;
        invite.type = GroupInviteType.LINK;
        invite.token = rawToken;
        invite.invitedBy = invitedBy;
        invite.expiresAt = null;
        return invite;
    }

    public void revoke() {
        this.revoked = true;
    }

    public void accept() {
        this.acceptedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return this.expiresAt != null && this.expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        if (this.revoked || isExpired()) {
            return false;
        }
        return this.type != GroupInviteType.EMAIL || this.acceptedAt == null;
    }
}
