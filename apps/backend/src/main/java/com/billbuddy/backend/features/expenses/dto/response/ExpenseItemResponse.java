package com.billbuddy.backend.features.expenses.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class ExpenseItemResponse {

    private Long id;
    private String name;
    private BigDecimal amount;
    private List<ExpenseItemAssignmentResponse> assignments;
}
