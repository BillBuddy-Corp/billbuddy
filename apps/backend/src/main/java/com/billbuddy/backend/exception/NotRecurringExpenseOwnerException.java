package com.billbuddy.backend.exception;

public class NotRecurringExpenseOwnerException extends RuntimeException {
    public NotRecurringExpenseOwnerException(String message) {
        super(message);
    }
}
