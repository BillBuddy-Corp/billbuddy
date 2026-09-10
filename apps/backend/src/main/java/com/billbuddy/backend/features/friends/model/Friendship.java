package com.billbuddy.backend.features.friends.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A friendship is a single undirected connection between two users, normalized so userLow
// always has the smaller id. This keeps one row per pair regardless of who added whom, and
// makes "are these two friends" a simple two-column lookup from either side.
@Entity
@Table(
        name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_low_id", "user_high_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_low_id", nullable = false)
    private User userLow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_high_id", nullable = false)
    private User userHigh;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Friendship create(User a, User b) {
        Friendship friendship = new Friendship();
        if (a.getId() < b.getId()) {
            friendship.userLow = a;
            friendship.userHigh = b;
        } else {
            friendship.userLow = b;
            friendship.userHigh = a;
        }
        return friendship;
    }

    public User theOtherUser(Long userId) {
        return userLow.getId().equals(userId) ? userHigh : userLow;
    }
}
