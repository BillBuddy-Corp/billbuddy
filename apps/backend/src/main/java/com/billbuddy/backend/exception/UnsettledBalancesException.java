package com.billbuddy.backend.exception;

public class UnsettledBalancesException extends RuntimeException {
    public UnsettledBalancesException(String message) {
        super(message);
    }
}
