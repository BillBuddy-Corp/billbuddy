package com.billbuddy.backend.features.expenses.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static ExpenseComment create(Expense expense, User user, String body) {
        ExpenseComment comment = new ExpenseComment();
        comment.expense = expense;
        comment.user = user;
        comment.body = body;
        return comment;
    }
}
