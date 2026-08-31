package com.billbuddy.backend.features.expenses.model;

import com.billbuddy.backend.features.auth.model.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "expense_item_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_item_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseItemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_item_id", nullable = false)
    private ExpenseItem expenseItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal share;

    public static ExpenseItemAssignment create(ExpenseItem expenseItem, User user, BigDecimal share) {
        ExpenseItemAssignment assignment = new ExpenseItemAssignment();
        assignment.expenseItem = expenseItem;
        assignment.user = user;
        assignment.share = share;
        return assignment;
    }
}
