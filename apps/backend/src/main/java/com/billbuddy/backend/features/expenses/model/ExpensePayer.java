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
        name = "expense_payers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpensePayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    public static ExpensePayer create(Expense expense, User user, BigDecimal amountPaid) {
        ExpensePayer payer = new ExpensePayer();
        payer.expense = expense;
        payer.user = user;
        payer.amountPaid = amountPaid;
        return payer;
    }
}
