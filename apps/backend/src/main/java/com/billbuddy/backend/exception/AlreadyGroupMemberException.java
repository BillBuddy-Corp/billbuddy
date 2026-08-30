package com.billbuddy.backend.exception;

public class AlreadyGroupMemberException extends RuntimeException {
    public AlreadyGroupMemberException(String message) {
        super(message);
    }
}
