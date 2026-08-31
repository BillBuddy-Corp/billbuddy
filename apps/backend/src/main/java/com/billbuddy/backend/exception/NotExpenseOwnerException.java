package com.billbuddy.backend.exception;

public class NotExpenseOwnerException extends RuntimeException {
    public NotExpenseOwnerException(String message) {
        super(message);
    }
}
