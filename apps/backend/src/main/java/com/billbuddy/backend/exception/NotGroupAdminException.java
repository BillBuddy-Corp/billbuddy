package com.billbuddy.backend.exception;

public class NotGroupAdminException extends RuntimeException {
    public NotGroupAdminException(String message) {
        super(message);
    }
}
