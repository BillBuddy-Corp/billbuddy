package com.billbuddy.backend.features.expenses.dto.request;

import com.billbuddy.backend.features.expenses.model.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateExpenseRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    // required when currency differs from the group's default currency
    @Positive(message = "exchangeRate must be positive")
    private BigDecimal exchangeRate;

    private String category;

    private Long receiptFileId;

    @NotNull(message = "splitType is required")
    private SplitType splitType;

    @NotEmpty(message = "At least one payer is required")
    @Valid
    private List<PayerEntry> payers;

    // required for splitType = EQUAL
    private List<Long> participantUserIds;

    // required for splitType = PERCENTAGE
    @Valid
    private List<PercentageEntry> percentages;

    // required for splitType = EXACT
    @Valid
    private List<ExactAmountEntry> exactAmounts;

    // required for splitType = ITEMIZED
    @Valid
    private List<ExpenseItemEntry> items;
}
