package com.billbuddy.backend.exception;

public class RecurringExpenseNotFoundException extends RuntimeException {
    public RecurringExpenseNotFoundException(String message) {
        super(message);
    }
}
