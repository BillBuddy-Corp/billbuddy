package com.billbuddy.backend.features.settlements.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    private Long userId;
    private String fullName;
    private BigDecimal netBalance;
}
