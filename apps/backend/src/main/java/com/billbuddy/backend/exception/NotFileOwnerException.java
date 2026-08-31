package com.billbuddy.backend.exception;

public class NotFileOwnerException extends RuntimeException {
    public NotFileOwnerException(String message) {
        super(message);
    }
}
