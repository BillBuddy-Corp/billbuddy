package com.billbuddy.backend.features.notifications.model;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.settlements.model.Settlement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Notification create(
            User user, NotificationType type, String message, Group group, Expense expense, Settlement settlement
    ) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.message = message;
        notification.group = group;
        notification.expense = expense;
        notification.settlement = settlement;
        return notification;
    }

    public void markRead() {
        this.readAt = LocalDateTime.now();
    }

    public boolean isRead() {
        return this.readAt != null;
    }
}
