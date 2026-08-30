package com.billbuddy.backend.exception;

public class NotGroupMemberException extends RuntimeException {
    public NotGroupMemberException(String message) {
        super(message);
    }
}
