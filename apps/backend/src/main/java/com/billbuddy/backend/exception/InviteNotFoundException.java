package com.billbuddy.backend.exception;

public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException(String message) {
        super(message);
    }
}
