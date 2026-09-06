package com.billbuddy.backend.exception;

public class FxRateLookupFailedException extends RuntimeException {
    public FxRateLookupFailedException(String message) {
        super(message);
    }

    public FxRateLookupFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
