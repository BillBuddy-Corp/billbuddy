package com.billbuddy.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleUserAlreadyExists(UserAlreadyExistsException ex) {

        return Map.of(
                "error", "USER_ALREADY_EXISTS",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        return Map.of(
                "error", "VALIDATION_FAILED",
                "errors", errors,
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUserNotFound(UserNotFoundException ex) {
        return Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleInvalidCredentials(InvalidCredentialsException ex) {
        return Map.of(
                "error", "INVALID_CREDENTIALS",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(GroupNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleGroupNotFound(GroupNotFoundException ex) {
        return Map.of(
                "error", "GROUP_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotGroupMemberException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotGroupMember(NotGroupMemberException ex) {
        return Map.of(
                "error", "NOT_GROUP_MEMBER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotGroupAdminException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotGroupAdmin(NotGroupAdminException ex) {
        return Map.of(
                "error", "NOT_GROUP_ADMIN",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(MemberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleMemberNotFound(MemberNotFoundException ex) {
        return Map.of(
                "error", "MEMBER_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(LastAdminException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleLastAdmin(LastAdminException ex) {
        return Map.of(
                "error", "LAST_ADMIN",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InviteNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleInviteNotFound(InviteNotFoundException ex) {
        return Map.of(
                "error", "INVITE_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidInviteException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidInvite(InvalidInviteException ex) {
        return Map.of(
                "error", "INVALID_INVITE",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(AlreadyGroupMemberException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleAlreadyGroupMember(AlreadyGroupMemberException ex) {
        return Map.of(
                "error", "ALREADY_GROUP_MEMBER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(UnsettledBalancesException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleUnsettledBalances(UnsettledBalancesException ex) {
        return Map.of(
                "error", "UNSETTLED_BALANCES",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidCurrency(InvalidCurrencyException ex) {
        return Map.of(
                "error", "INVALID_CURRENCY",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleExpenseNotFound(ExpenseNotFoundException ex) {
        return Map.of(
                "error", "EXPENSE_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidSplitException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidSplit(InvalidSplitException ex) {
        return Map.of(
                "error", "INVALID_SPLIT",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidExpenseParticipantException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidExpenseParticipant(InvalidExpenseParticipantException ex) {
        return Map.of(
                "error", "INVALID_EXPENSE_PARTICIPANT",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotExpenseOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotExpenseOwner(NotExpenseOwnerException ex) {
        return Map.of(
                "error", "NOT_EXPENSE_OWNER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(SettlementNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleSettlementNotFound(SettlementNotFoundException ex) {
        return Map.of(
                "error", "SETTLEMENT_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidSettlementException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidSettlement(InvalidSettlementException ex) {
        return Map.of(
                "error", "INVALID_SETTLEMENT",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidSettlementParticipantException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidSettlementParticipant(InvalidSettlementParticipantException ex) {
        return Map.of(
                "error", "INVALID_SETTLEMENT_PARTICIPANT",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotSettlementOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotSettlementOwner(NotSettlementOwnerException ex) {
        return Map.of(
                "error", "NOT_SETTLEMENT_OWNER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidAuthTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidAuthToken(InvalidAuthTokenException ex) {
        return Map.of(
                "error", "INVALID_AUTH_TOKEN",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidOtpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidOtp(InvalidOtpException ex) {
        return Map.of(
                "error", "INVALID_OTP",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(MobileNumberNotSetException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMobileNumberNotSet(MobileNumberNotSetException ex) {
        return Map.of(
                "error", "MOBILE_NUMBER_NOT_SET",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(StoredFileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleStoredFileNotFound(StoredFileNotFoundException ex) {
        return Map.of(
                "error", "STORED_FILE_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidFile(InvalidFileException ex) {
        return Map.of(
                "error", "INVALID_FILE",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotFileOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotFileOwner(NotFileOwnerException ex) {
        return Map.of(
                "error", "NOT_FILE_OWNER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(ReceiptScanFailedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleReceiptScanFailed(ReceiptScanFailedException ex) {
        return Map.of(
                "error", "RECEIPT_SCAN_FAILED",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(FxRateLookupFailedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleFxRateLookupFailed(FxRateLookupFailedException ex) {
        return Map.of(
                "error", "FX_RATE_LOOKUP_FAILED",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidRecurrenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidRecurrence(InvalidRecurrenceException ex) {
        return Map.of(
                "error", "INVALID_RECURRENCE",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(RecurringExpenseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleRecurringExpenseNotFound(RecurringExpenseNotFoundException ex) {
        return Map.of(
                "error", "RECURRING_EXPENSE_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotRecurringExpenseOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotRecurringExpenseOwner(NotRecurringExpenseOwnerException ex) {
        return Map.of(
                "error", "NOT_RECURRING_EXPENSE_OWNER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(ExpenseCommentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleExpenseCommentNotFound(ExpenseCommentNotFoundException ex) {
        return Map.of(
                "error", "EXPENSE_COMMENT_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotExpenseCommentOwnerException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotExpenseCommentOwner(NotExpenseCommentOwnerException ex) {
        return Map.of(
                "error", "NOT_EXPENSE_COMMENT_OWNER",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotificationNotFound(NotificationNotFoundException ex) {
        return Map.of(
                "error", "NOTIFICATION_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(FriendNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleFriendNotFound(FriendNotFoundException ex) {
        return Map.of(
                "error", "FRIEND_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(AlreadyFriendsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleAlreadyFriends(AlreadyFriendsException ex) {
        return Map.of(
                "error", "ALREADY_FRIENDS",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(FriendshipNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleFriendshipNotFound(FriendshipNotFoundException ex) {
        return Map.of(
                "error", "FRIENDSHIP_NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidFriendException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidFriend(InvalidFriendException ex) {
        return Map.of(
                "error", "INVALID_FRIEND",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );
    }

}
