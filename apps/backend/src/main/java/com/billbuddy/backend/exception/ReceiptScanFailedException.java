package com.billbuddy.backend.exception;

public class ReceiptScanFailedException extends RuntimeException {
    public ReceiptScanFailedException(String message) {
        super(message);
    }
}
