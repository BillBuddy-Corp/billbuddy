package com.billbuddy.backend.exception;

public class MobileNumberNotSetException extends RuntimeException {
    public MobileNumberNotSetException(String message) {
        super(message);
    }
}
