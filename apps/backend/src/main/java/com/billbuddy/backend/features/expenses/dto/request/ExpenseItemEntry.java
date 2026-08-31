package com.billbuddy.backend.features.expenses.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseItemEntry {

    @NotBlank(message = "Item name is required")
    private String name;

    @NotNull(message = "Item amount is required")
    @DecimalMin(value = "0.01", message = "Item amount must be positive")
    private BigDecimal amount;

    @NotEmpty(message = "Each item needs at least one assignment")
    @Valid
    private List<ItemAssignmentEntry> assignments;
}
