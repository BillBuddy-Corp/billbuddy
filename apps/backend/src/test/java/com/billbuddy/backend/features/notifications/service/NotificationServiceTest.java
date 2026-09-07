package com.billbuddy.backend.features.notifications.service;

import com.billbuddy.backend.common.PushNotificationService;
import com.billbuddy.backend.exception.NotificationNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseComment;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.notifications.dto.response.NotificationResponse;
import com.billbuddy.backend.features.notifications.model.Notification;
import com.billbuddy.backend.features.notifications.model.NotificationType;
import com.billbuddy.backend.features.notifications.repository.NotificationRepository;
import com.billbuddy.backend.features.settlements.model.Settlement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private NotificationService notificationService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator) {
        Group group = Group.create("Goa Trip", "desc", "INR", creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private Expense buildExpense(Long id, Group group, User createdBy) {
        Expense expense = Expense.create(
                group, createdBy, "Dinner", new BigDecimal("90"), "INR",
                new BigDecimal("90"), BigDecimal.ONE, null, null, SplitType.EQUAL
        );
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }

    // ===================== notifyExpenseCreated =====================

    @Test
    void notifyExpenseCreated_notifiesOtherActiveMembers_excludingActor() {
        User creator = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, creator);
        Expense expense = buildExpense(100L, group, creator);

        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                GroupMember.createAdmin(group, creator),
                GroupMember.createMember(group, member1),
                GroupMember.createMember(group, member2)
        ));

        notificationService.notifyExpenseCreated(expense, 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Long> recipientIds = captor.getAllValues().stream().map(n -> n.getUser().getId()).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(2L, 3L);
        assertThat(captor.getAllValues().get(0).getType()).isEqualTo(NotificationType.EXPENSE_CREATED);
        assertThat(captor.getAllValues().get(0).getMessage()).contains("User 1").contains("Dinner");
    }

    @Test
    void notifyExpenseCreated_sendsPush_whenRecipientHasFcmToken() {
        User creator = buildUser(1L);
        User member1 = buildUser(2L);
        member1.updateProfile(member1.getFullName(), null, member1.getDefaultCurrency(), null, "fcm-token-abc");
        Group group = buildGroup(10L, creator);
        Expense expense = buildExpense(100L, group, creator);

        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                GroupMember.createAdmin(group, creator),
                GroupMember.createMember(group, member1)
        ));

        notificationService.notifyExpenseCreated(expense, 1L);

        verify(pushNotificationService).send(eq("fcm-token-abc"), any(), any());
    }

    @Test
    void notifyExpenseCreated_skipsPush_whenRecipientHasNoFcmToken() {
        User creator = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, creator);
        Expense expense = buildExpense(100L, group, creator);

        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                GroupMember.createAdmin(group, creator),
                GroupMember.createMember(group, member1)
        ));

        notificationService.notifyExpenseCreated(expense, 1L);

        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void notifyExpenseCreated_neverThrows_whenSaveFailsForOneRecipient() {
        User creator = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, creator);
        Expense expense = buildExpense(100L, group, creator);

        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                GroupMember.createAdmin(group, creator),
                GroupMember.createMember(group, member1),
                GroupMember.createMember(group, member2)
        ));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db hiccup"))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyExpenseCreated(expense, 1L);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // ===================== notifyCommentPosted =====================

    @Test
    void notifyCommentPosted_notifiesOtherActiveMembers_excludingAuthor() {
        User author = buildUser(2L);
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        Expense expense = buildExpense(100L, group, admin);
        ExpenseComment comment = ExpenseComment.create(expense, author, "Looks right");
        ReflectionTestUtils.setField(comment, "id", 500L);

        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                GroupMember.createAdmin(group, admin),
                GroupMember.createMember(group, author)
        ));

        notificationService.notifyCommentPosted(comment, 2L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.COMMENT_POSTED);
        assertThat(captor.getValue().getMessage()).contains("User 2").contains("Looks right");
    }

    // ===================== notifySettlementRecorded =====================

    @Test
    void notifySettlementRecorded_notifiesOnlyBothParties_excludingActor() {
        User payer = buildUser(1L);
        User payee = buildUser(2L);
        User bystander = buildUser(3L);
        Group group = buildGroup(10L, payer);
        Settlement settlement = Settlement.create(group, payer, payee, payer, new BigDecimal("50"), "INR", null);
        ReflectionTestUtils.setField(settlement, "id", 900L);

        notificationService.notifySettlementRecorded(settlement, 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.SETTLEMENT_RECORDED);
        assertThat(captor.getValue().getMessage()).contains("User 1").contains("User 2").contains("50");
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void notifySettlementRecorded_notifiesBothParties_whenActorIsThirdParty() {
        User payer = buildUser(1L);
        User payee = buildUser(2L);
        User admin = buildUser(3L);
        Group group = buildGroup(10L, admin);
        Settlement settlement = Settlement.create(group, payer, payee, admin, new BigDecimal("50"), "INR", null);
        ReflectionTestUtils.setField(settlement, "id", 900L);

        notificationService.notifySettlementRecorded(settlement, 3L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Long> recipientIds = captor.getAllValues().stream().map(n -> n.getUser().getId()).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(1L, 2L);
    }

    // ===================== listNotifications / unreadCount =====================

    @Test
    void listNotifications_mapsToResponses() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        Notification notification = Notification.create(user, NotificationType.EXPENSE_CREATED, "test message", group, null, null);
        ReflectionTestUtils.setField(notification, "id", 1L);

        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.listNotifications(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("test message");
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(0).getExpenseId()).isNull();
    }

    @Test
    void unreadCount_returnsRepositoryCount() {
        when(notificationRepository.countByUser_IdAndReadAtIsNull(1L)).thenReturn(3L);

        assertThat(notificationService.unreadCount(1L)).isEqualTo(3L);
    }

    // ===================== markAsRead / markAllAsRead =====================

    @Test
    void markAsRead_marksNotificationRead_whenOwnedByCaller() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        Notification notification = Notification.create(user, NotificationType.EXPENSE_CREATED, "test", group, null, null);
        when(notificationRepository.findByIdAndUser_Id(500L, 1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(500L, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsRead_throwsNotFound_whenNotOwnedByCaller() {
        when(notificationRepository.findByIdAndUser_Id(500L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(500L, 2L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAllAsRead_delegatesToRepository() {
        notificationService.markAllAsRead(1L);

        verify(notificationRepository).markAllAsRead(1L);
    }
}
