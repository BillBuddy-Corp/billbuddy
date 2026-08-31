package com.billbuddy.backend.exception;

public class NotSettlementOwnerException extends RuntimeException {
    public NotSettlementOwnerException(String message) {
        super(message);
    }
}
