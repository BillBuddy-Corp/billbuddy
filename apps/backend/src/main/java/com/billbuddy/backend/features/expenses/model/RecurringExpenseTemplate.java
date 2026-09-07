package com.billbuddy.backend.features.expenses.model;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.model.Group;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "recurring_expense_templates")
public class RecurringExpenseTemplate {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate;

    @Column
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    // JSON blob of whatever's variable per splitType: payers, plus participantUserIds/
    // percentages/exactAmounts/items. Never queried on, only ever read back whole to replay
    // one CreateExpenseRequest -- not worth 4-5 relational child tables for that.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "split_config", nullable = false, columnDefinition = "jsonb")
    private String splitConfig;

    @Column(name = "next_run_at", nullable = false)
    private LocalDate nextRunAt;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RecurringExpenseTemplate(
            Group group,
            User createdBy,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRate,
            String category,
            SplitType splitType,
            RecurringFrequency frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            String splitConfig,
            LocalDate nextRunAt
    ) {
        this.group = group;
        this.createdBy = createdBy;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.category = category;
        this.splitType = splitType;
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.splitConfig = splitConfig;
        this.nextRunAt = nextRunAt;
    }

    public static RecurringExpenseTemplate create(
            Group group,
            User createdBy,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRate,
            String category,
            SplitType splitType,
            RecurringFrequency frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            String splitConfig,
            LocalDate nextRunAt
    ) {
        return RecurringExpenseTemplate.builder()
                .group(group)
                .createdBy(createdBy)
                .description(description)
                .amount(amount)
                .currency(currency)
                .exchangeRate(exchangeRate)
                .category(category)
                .splitType(splitType)
                .frequency(frequency)
                .dayOfWeek(dayOfWeek)
                .dayOfMonth(dayOfMonth)
                .splitConfig(splitConfig)
                .nextRunAt(nextRunAt)
                .build();
    }

    public void pause() {
        this.active = false;
    }

    public void resume() {
        this.active = true;
    }

    public void cancel() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isCancelled() {
        return this.deletedAt != null;
    }

    public void advanceNextRun(LocalDate next) {
        this.nextRunAt = next;
    }
}
