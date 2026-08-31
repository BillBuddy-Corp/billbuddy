package com.billbuddy.backend.features.settlements.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class UpdateSettlementRequest {

    @NotNull(message = "paidByUserId is required")
    private Long paidByUserId;

    @NotNull(message = "paidToUserId is required")
    private Long paidToUserId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String note;
}
