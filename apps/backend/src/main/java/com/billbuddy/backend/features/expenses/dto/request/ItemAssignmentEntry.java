package com.billbuddy.backend.features.expenses.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ItemAssignmentEntry {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "share is required")
    @DecimalMin(value = "0.01", message = "share must be positive")
    private BigDecimal share;
}
