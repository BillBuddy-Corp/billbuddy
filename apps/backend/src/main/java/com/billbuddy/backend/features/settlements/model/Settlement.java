package com.billbuddy.backend.features.settlements.model;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.model.Group;
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
@Table(name = "settlements")
public class Settlement {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_to", nullable = false)
    private User paidTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Settlement(
            Group group,
            User paidBy,
            User paidTo,
            User createdBy,
            BigDecimal amount,
            String currency,
            String note
    ) {
        this.group = group;
        this.paidBy = paidBy;
        this.paidTo = paidTo;
        this.createdBy = createdBy;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
    }

    public static Settlement create(
            Group group,
            User paidBy,
            User paidTo,
            User createdBy,
            BigDecimal amount,
            String currency,
            String note
    ) {
        return Settlement.builder()
                .group(group)
                .paidBy(paidBy)
                .paidTo(paidTo)
                .createdBy(createdBy)
                .amount(amount)
                .currency(currency)
                .note(note)
                .build();
    }

    public void update(User paidBy, User paidTo, BigDecimal amount, String currency, String note) {
        this.paidBy = paidBy;
        this.paidTo = paidTo;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
