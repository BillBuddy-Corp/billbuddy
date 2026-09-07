package com.billbuddy.backend.exception;

public class InvalidRecurrenceException extends RuntimeException {
    public InvalidRecurrenceException(String message) {
        super(message);
    }
}
