package com.billbuddy.backend.features.expenses.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class PercentageEntry {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "percentage is required")
    @DecimalMin(value = "0.01", message = "percentage must be positive")
    @DecimalMax(value = "100", message = "percentage cannot exceed 100")
    private BigDecimal percentage;
}
