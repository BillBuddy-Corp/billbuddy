package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.ExpenseCommentNotFoundException;
import com.billbuddy.backend.exception.ExpenseNotFoundException;
import com.billbuddy.backend.exception.NotExpenseCommentOwnerException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseCommentRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseCommentResponse;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseComment;
import com.billbuddy.backend.features.expenses.repository.ExpenseCommentRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseCommentService {

    private final ExpenseCommentRepository expenseCommentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;
    private final NotificationService notificationService;

    public ExpenseCommentService(
            ExpenseCommentRepository expenseCommentRepository,
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService,
            NotificationService notificationService
    ) {
        this.expenseCommentRepository = expenseCommentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ExpenseCommentResponse createComment(Long expenseId, Long requesterId, CreateExpenseCommentRequest request) {
        Expense expense = requireExpense(expenseId);
        groupAccessService.requireActiveMember(expense.getGroup().getId(), requesterId);
        User author = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ExpenseComment comment = ExpenseComment.create(expense, author, request.getBody());
        comment = expenseCommentRepository.save(comment);
        notificationService.notifyCommentPosted(comment, requesterId);

        return toResponse(comment);
    }

    @Transactional
    public List<ExpenseCommentResponse> listComments(Long expenseId, Long requesterId) {
        Expense expense = requireExpense(expenseId);
        groupAccessService.requireActiveMember(expense.getGroup().getId(), requesterId);

        return expenseCommentRepository.findByExpense_IdOrderByCreatedAtAsc(expenseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteComment(Long expenseId, Long commentId, Long requesterId) {
        Expense expense = requireExpense(expenseId);
        GroupMember requesterMembership = groupAccessService.requireActiveMember(expense.getGroup().getId(), requesterId);

        ExpenseComment comment = expenseCommentRepository.findById(commentId)
                .filter(c -> c.getExpense().getId().equals(expenseId))
                .orElseThrow(() -> new ExpenseCommentNotFoundException("Comment not found"));

        boolean isAuthor = comment.getUser().getId().equals(requesterId);
        boolean isAdmin = requesterMembership.getRole() == GroupRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new NotExpenseCommentOwnerException("Only the comment's author or a group admin can delete it");
        }

        expenseCommentRepository.delete(comment);
    }

    private Expense requireExpense(Long expenseId) {
        return expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
    }

    private ExpenseCommentResponse toResponse(ExpenseComment comment) {
        return new ExpenseCommentResponse(
                comment.getId(),
                comment.getExpense().getId(),
                comment.getBody(),
                comment.getUser().getId(),
                comment.getUser().getFullName(),
                comment.getCreatedAt()
        );
    }
}
