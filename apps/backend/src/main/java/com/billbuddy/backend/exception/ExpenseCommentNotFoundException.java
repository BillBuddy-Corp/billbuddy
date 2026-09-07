package com.billbuddy.backend.exception;

public class ExpenseCommentNotFoundException extends RuntimeException {
    public ExpenseCommentNotFoundException(String message) {
        super(message);
    }
}
