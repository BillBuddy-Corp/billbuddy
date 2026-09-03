package com.billbuddy.backend.exception;

public class InvalidAuthTokenException extends RuntimeException {
    public InvalidAuthTokenException(String message) {
        super(message);
    }
}
