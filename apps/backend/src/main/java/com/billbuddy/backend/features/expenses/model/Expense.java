package com.billbuddy.backend.features.expenses.model;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.storage.model.StoredFile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null for a non-group (friend-to-friend) expense -- exactly one of group or the
    // friendUserLowId/friendUserHighId pair below is set, never both.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    // Set only when group is null, normalized so friendUserLowId is always the smaller id --
    // same convention as Friendship, and lets "all non-group expenses between these two people"
    // be a direct indexed lookup instead of a join through splits/payers.
    @Column(name = "friend_user_low_id")
    private Long friendUserLowId;

    @Column(name = "friend_user_high_id")
    private Long friendUserHighId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "converted_amount", nullable = false)
    private BigDecimal convertedAmount;

    @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal exchangeRate;

    @Column
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_file_id")
    private StoredFile receiptFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Expense(
            Group group,
            Long friendUserLowId,
            Long friendUserHighId,
            User createdBy,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal convertedAmount,
            BigDecimal exchangeRate,
            String category,
            StoredFile receiptFile,
            SplitType splitType
    ) {
        this.group = group;
        this.friendUserLowId = friendUserLowId;
        this.friendUserHighId = friendUserHighId;
        this.createdBy = createdBy;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.convertedAmount = convertedAmount;
        this.exchangeRate = exchangeRate;
        this.category = category;
        this.receiptFile = receiptFile;
        this.splitType = splitType;
    }

    public static Expense create(
            Group group,
            User createdBy,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal convertedAmount,
            BigDecimal exchangeRate,
            String category,
            StoredFile receiptFile,
            SplitType splitType
    ) {
        return Expense.builder()
                .group(group)
                .createdBy(createdBy)
                .description(description)
                .amount(amount)
                .currency(currency)
                .convertedAmount(convertedAmount)
                .exchangeRate(exchangeRate)
                .category(category)
                .receiptFile(receiptFile)
                .splitType(splitType)
                .build();
    }

    // The two participants can be passed in either order -- normalized internally the same way
    // Friendship.create() does it, so callers never need to know about the low/high convention.
    public static Expense createFriendExpense(
            User userA,
            User userB,
            User createdBy,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal convertedAmount,
            BigDecimal exchangeRate,
            String category,
            StoredFile receiptFile,
            SplitType splitType
    ) {
        Long lowId = Math.min(userA.getId(), userB.getId());
        Long highId = Math.max(userA.getId(), userB.getId());
        return Expense.builder()
                .friendUserLowId(lowId)
                .friendUserHighId(highId)
                .createdBy(createdBy)
                .description(description)
                .amount(amount)
                .currency(currency)
                .convertedAmount(convertedAmount)
                .exchangeRate(exchangeRate)
                .category(category)
                .receiptFile(receiptFile)
                .splitType(splitType)
                .build();
    }

    public void update(
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal convertedAmount,
            BigDecimal exchangeRate,
            String category,
            StoredFile receiptFile,
            SplitType splitType
    ) {
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.convertedAmount = convertedAmount;
        this.exchangeRate = exchangeRate;
        this.category = category;
        this.receiptFile = receiptFile;
        this.splitType = splitType;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isFriendExpense() {
        return this.group == null;
    }
}
