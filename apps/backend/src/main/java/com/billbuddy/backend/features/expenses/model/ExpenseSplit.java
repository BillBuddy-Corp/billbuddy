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
        name = "expense_splits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount_owed", nullable = false)
    private BigDecimal amountOwed;

    // only populated for PERCENTAGE-type expenses
    @Column
    private BigDecimal percentage;

    public static ExpenseSplit create(Expense expense, User user, BigDecimal amountOwed, BigDecimal percentage) {
        ExpenseSplit split = new ExpenseSplit();
        split.expense = expense;
        split.user = user;
        split.amountOwed = amountOwed;
        split.percentage = percentage;
        return split;
    }
}
