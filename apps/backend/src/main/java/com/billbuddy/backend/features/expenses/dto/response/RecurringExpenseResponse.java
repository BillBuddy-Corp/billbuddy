package com.billbuddy.backend.features.expenses.dto.response;

import com.billbuddy.backend.features.expenses.dto.request.ExactAmountEntry;
import com.billbuddy.backend.features.expenses.dto.request.ExpenseItemEntry;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.request.PercentageEntry;
import com.billbuddy.backend.features.expenses.model.RecurringFrequency;
import com.billbuddy.backend.features.expenses.model.SplitType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class RecurringExpenseResponse {

    private Long id;
    private Long groupId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private BigDecimal exchangeRate;
    private String category;
    private SplitType splitType;
    private List<PayerEntry> payers;
    private List<Long> participantUserIds;
    private List<PercentageEntry> percentages;
    private List<ExactAmountEntry> exactAmounts;
    private List<ExpenseItemEntry> items;
    private RecurringFrequency frequency;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private LocalDate nextRunAt;
    private boolean active;
    private Long createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
