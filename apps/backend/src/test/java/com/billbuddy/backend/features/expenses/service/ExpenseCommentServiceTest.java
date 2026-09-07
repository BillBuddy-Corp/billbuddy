package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.ExpenseCommentNotFoundException;
import com.billbuddy.backend.exception.ExpenseNotFoundException;
import com.billbuddy.backend.exception.NotExpenseCommentOwnerException;
import com.billbuddy.backend.exception.NotGroupMemberException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseCommentRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseCommentResponse;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseComment;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.ExpenseCommentRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ExpenseCommentServiceTest {

    @Mock
    private ExpenseCommentRepository expenseCommentRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ExpenseCommentService expenseCommentService;

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
                group, createdBy, "Dinner", new BigDecimal("90"), group.getDefaultCurrency(),
                new BigDecimal("90"), BigDecimal.ONE, null, null, SplitType.EQUAL
        );
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }

    private ExpenseComment buildComment(Long id, Expense expense, User author, String body) {
        ExpenseComment comment = ExpenseComment.create(expense, author, body);
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private CreateExpenseCommentRequest buildRequest(String body) {
        CreateExpenseCommentRequest request = new CreateExpenseCommentRequest();
        request.setBody(body);
        return request;
    }

    // ===================== CREATE =====================

    @Test
    void createComment_succeeds_whenActiveMember() {
        User author = buildUser(1L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, author));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(expenseCommentRepository.save(any(ExpenseComment.class))).thenAnswer(inv -> {
            ExpenseComment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        });

        ExpenseCommentResponse response = expenseCommentService.createComment(100L, 1L, buildRequest("Looks right to me"));

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getExpenseId()).isEqualTo(100L);
        assertThat(response.getBody()).isEqualTo("Looks right to me");
        assertThat(response.getAuthorUserId()).isEqualTo(1L);
        assertThat(response.getAuthorName()).isEqualTo("User 1");
        verify(notificationService).notifyCommentPosted(any(ExpenseComment.class), eq(1L));
    }

    @Test
    void createComment_throwsExpenseNotFound_whenExpenseMissing() {
        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseCommentService.createComment(100L, 1L, buildRequest("Hi")))
                .isInstanceOf(ExpenseNotFoundException.class);

        verify(expenseCommentRepository, never()).save(any());
    }

    @Test
    void createComment_throwsNotGroupMember_whenRequesterNotActiveMember() {
        User creator = buildUser(1L);
        Group group = buildGroup(10L, creator);
        Expense expense = buildExpense(100L, group, creator);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenThrow(new NotGroupMemberException("You are not a member of this group"));

        assertThatThrownBy(() -> expenseCommentService.createComment(100L, 2L, buildRequest("Hi")))
                .isInstanceOf(NotGroupMemberException.class);

        verify(expenseCommentRepository, never()).save(any());
    }

    // ===================== LIST =====================

    @Test
    void listComments_returnsOldestFirst() {
        User author = buildUser(1L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);
        ExpenseComment first = buildComment(1L, expense, author, "First");
        ExpenseComment second = buildComment(2L, expense, author, "Second");

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, author));
        when(expenseCommentRepository.findByExpense_IdOrderByCreatedAtAsc(100L)).thenReturn(List.of(first, second));

        List<ExpenseCommentResponse> result = expenseCommentService.listComments(100L, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBody()).isEqualTo("First");
        assertThat(result.get(1).getBody()).isEqualTo("Second");
    }

    @Test
    void listComments_throwsExpenseNotFound_whenExpenseSoftDeleted() {
        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseCommentService.listComments(100L, 1L))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    // ===================== DELETE =====================

    @Test
    void deleteComment_succeeds_whenAuthor() {
        User author = buildUser(1L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);
        ExpenseComment comment = buildComment(500L, expense, author, "Oops typo");

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createMember(group, author));
        when(expenseCommentRepository.findById(500L)).thenReturn(Optional.of(comment));

        expenseCommentService.deleteComment(100L, 500L, 1L);

        verify(expenseCommentRepository).delete(comment);
    }

    @Test
    void deleteComment_succeeds_whenGroupAdminButNotAuthor() {
        User author = buildUser(1L);
        User admin = buildUser(2L);
        Group group = buildGroup(10L, admin);
        Expense expense = buildExpense(100L, group, author);
        ExpenseComment comment = buildComment(500L, expense, author, "Some comment");

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(expenseCommentRepository.findById(500L)).thenReturn(Optional.of(comment));

        expenseCommentService.deleteComment(100L, 500L, 2L);

        verify(expenseCommentRepository).delete(comment);
    }

    @Test
    void deleteComment_throwsNotOwner_whenNeitherAuthorNorAdmin() {
        User author = buildUser(1L);
        User other = buildUser(2L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);
        ExpenseComment comment = buildComment(500L, expense, author, "Some comment");

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, other));
        when(expenseCommentRepository.findById(500L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> expenseCommentService.deleteComment(100L, 500L, 2L))
                .isInstanceOf(NotExpenseCommentOwnerException.class);

        verify(expenseCommentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_throwsNotFound_whenCommentBelongsToDifferentExpense() {
        User author = buildUser(1L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);
        Expense otherExpense = buildExpense(200L, group, author);
        ExpenseComment comment = buildComment(500L, otherExpense, author, "Wrong expense");

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, author));
        when(expenseCommentRepository.findById(500L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> expenseCommentService.deleteComment(100L, 500L, 1L))
                .isInstanceOf(ExpenseCommentNotFoundException.class);

        verify(expenseCommentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_throwsNotFound_whenCommentMissing() {
        User author = buildUser(1L);
        Group group = buildGroup(10L, author);
        Expense expense = buildExpense(100L, group, author);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, author));
        when(expenseCommentRepository.findById(500L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseCommentService.deleteComment(100L, 500L, 1L))
                .isInstanceOf(ExpenseCommentNotFoundException.class);
    }
}
