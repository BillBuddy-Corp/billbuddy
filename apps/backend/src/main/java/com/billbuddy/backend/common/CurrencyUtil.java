package com.billbuddy.backend.common;

import com.billbuddy.backend.exception.InvalidCurrencyException;

import java.util.Currency;

public final class CurrencyUtil {

    private CurrencyUtil() {
        // prevent instantiation
    }

    public static String normalize(String currency) {
        String normalized = currency == null ? null : currency.trim().toUpperCase();
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidCurrencyException("Invalid currency code: " + currency);
        }
    }
}
