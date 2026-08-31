package com.billbuddy.backend.features.expenses.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ExpenseSplitResponse {

    private Long userId;
    private String fullName;
    private BigDecimal amountOwed;
    private BigDecimal percentage;
}
