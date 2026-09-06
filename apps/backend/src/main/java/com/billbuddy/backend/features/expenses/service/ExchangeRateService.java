package com.billbuddy.backend.features.expenses.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateService {

    ExchangeRateQuote getRate(String fromCurrency, String toCurrency);

    record ExchangeRateQuote(BigDecimal rate, LocalDate asOf) {
    }
}
