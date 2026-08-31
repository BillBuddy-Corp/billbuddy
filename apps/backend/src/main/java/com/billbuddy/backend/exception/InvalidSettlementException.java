package com.billbuddy.backend.exception;

public class InvalidSettlementException extends RuntimeException {
    public InvalidSettlementException(String message) {
        super(message);
    }
}
