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

    public static String normalizeAndRequireMatch(String currency, String requiredCurrency, String context) {
        String normalized = normalize(currency);
        if (!normalized.equals(requiredCurrency)) {
            throw new InvalidCurrencyException(
                    context + " currency must match the group's default currency (" + requiredCurrency + ")"
            );
        }
        return normalized;
    }
}
