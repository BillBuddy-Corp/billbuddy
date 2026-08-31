package com.billbuddy.backend.exception;

public class InvalidExpenseParticipantException extends RuntimeException {
    public InvalidExpenseParticipantException(String message) {
        super(message);
    }
}
