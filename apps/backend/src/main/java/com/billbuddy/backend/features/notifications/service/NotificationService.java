package com.billbuddy.backend.features.notifications.service;

import com.billbuddy.backend.common.PushNotificationService;
import com.billbuddy.backend.exception.NotificationNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseComment;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.notifications.dto.response.NotificationResponse;
import com.billbuddy.backend.features.notifications.model.Notification;
import com.billbuddy.backend.features.notifications.model.NotificationType;
import com.billbuddy.backend.features.notifications.repository.NotificationRepository;
import com.billbuddy.backend.features.settlements.model.Settlement;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PushNotificationService pushNotificationService;

    public NotificationService(
            NotificationRepository notificationRepository,
            GroupMemberRepository groupMemberRepository,
            PushNotificationService pushNotificationService
    ) {
        this.notificationRepository = notificationRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.pushNotificationService = pushNotificationService;
    }

    // The three notify* methods below never throw -- a bad recipient or unexpected error
    // notifying one person is caught and logged so it can't stop the others, and callers
    // (ExpenseService etc.) can call these as plain fire-and-forget with no try/catch of their
    // own. Safe to do because a notification row is a local DB write in the same transaction as
    // the triggering action, not a separate-transaction concern like the OTP attempt counter was.

    public void notifyExpenseCreated(Expense expense, Long actorId) {
        User actor = expense.getCreatedBy();
        String message = actor.getFullName() + " added an expense: " + expense.getDescription()
                + " (" + expense.getAmount() + " " + expense.getCurrency() + ")";
        notifyOtherActiveMembers(expense.getGroup(), actorId, NotificationType.EXPENSE_CREATED, message, expense, null);
    }

    public void notifyCommentPosted(ExpenseComment comment, Long actorId) {
        User actor = comment.getUser();
        Expense expense = comment.getExpense();
        String message = actor.getFullName() + " commented on \"" + expense.getDescription() + "\": " + comment.getBody();
        notifyOtherActiveMembers(expense.getGroup(), actorId, NotificationType.COMMENT_POSTED, message, expense, null);
    }

    public void notifySettlementRecorded(Settlement settlement, Long actorId) {
        String message = settlement.getPaidBy().getFullName() + " paid " + settlement.getPaidTo().getFullName()
                + " " + settlement.getAmount() + " " + settlement.getCurrency();

        List<User> recipients = List.of(settlement.getPaidBy(), settlement.getPaidTo()).stream()
                .filter(user -> !user.getId().equals(actorId))
                .distinct()
                .toList();

        for (User recipient : recipients) {
            createAndSend(recipient, NotificationType.SETTLEMENT_RECORDED, message, settlement.getGroup(), null, settlement);
        }
    }

    @Transactional
    public List<NotificationResponse> listNotifications(Long userId) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public long unreadCount(Long userId) {
        return notificationRepository.countByUser_IdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
        notification.markRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private void notifyOtherActiveMembers(
            Group group, Long actorId, NotificationType type, String message, Expense expense, Settlement settlement
    ) {
        List<User> recipients = groupMemberRepository.findByGroup_IdAndLeftAtIsNull(group.getId()).stream()
                .map(GroupMember::getUser)
                .filter(user -> !user.getId().equals(actorId))
                .toList();

        for (User recipient : recipients) {
            createAndSend(recipient, type, message, group, expense, settlement);
        }
    }

    private void createAndSend(
            User recipient, NotificationType type, String message, Group group, Expense expense, Settlement settlement
    ) {
        try {
            Notification notification = Notification.create(recipient, type, message, group, expense, settlement);
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.error("Failed to create notification for userId={}, type={}", recipient.getId(), type, ex);
            return;
        }

        if (recipient.getFcmToken() != null) {
            try {
                pushNotificationService.send(recipient.getFcmToken(), type.name(), message);
            } catch (Exception ex) {
                log.warn("Failed to send push notification for userId={}", recipient.getId(), ex);
            }
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getGroup().getId(),
                notification.getExpense() == null ? null : notification.getExpense().getId(),
                notification.getSettlement() == null ? null : notification.getSettlement().getId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
