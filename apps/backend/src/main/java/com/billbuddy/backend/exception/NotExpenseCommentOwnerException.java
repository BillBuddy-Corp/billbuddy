package com.billbuddy.backend.exception;

public class NotExpenseCommentOwnerException extends RuntimeException {
    public NotExpenseCommentOwnerException(String message) {
        super(message);
    }
}
