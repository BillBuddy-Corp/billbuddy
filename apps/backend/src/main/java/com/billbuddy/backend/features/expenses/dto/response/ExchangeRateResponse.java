package com.billbuddy.backend.features.expenses.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ExchangeRateResponse {

    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private LocalDate asOf;
}
