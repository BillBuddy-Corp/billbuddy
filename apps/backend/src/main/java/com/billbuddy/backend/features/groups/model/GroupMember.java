package com.billbuddy.backend.features.groups.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    public static GroupMember createAdmin(Group group, User user) {
        return create(group, user, GroupRole.ADMIN);
    }

    public static GroupMember createMember(Group group, User user) {
        return create(group, user, GroupRole.MEMBER);
    }

    private static GroupMember create(Group group, User user, GroupRole role) {
        GroupMember member = new GroupMember();
        member.group = group;
        member.user = user;
        member.role = role;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    public boolean isActive() {
        return this.leftAt == null;
    }

    public void leave() {
        this.leftAt = LocalDateTime.now();
    }

    public void rejoin() {
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
        this.role = GroupRole.MEMBER;
    }

    public void changeRole(GroupRole newRole) {
        this.role = newRole;
    }
}
