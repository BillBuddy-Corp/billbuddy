package com.billbuddy.backend.features.expenses.dto.response;

import com.billbuddy.backend.features.expenses.model.SplitType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private Long groupId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private String category;
    private String receiptUrl;
    private SplitType splitType;
    private Long createdByUserId;
    private String createdByName;
    private List<ExpensePayerResponse> payers;
    private List<ExpenseSplitResponse> splits;

    // only populated when splitType == ITEMIZED
    private List<ExpenseItemResponse> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
