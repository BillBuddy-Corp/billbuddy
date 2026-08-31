package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.common.CurrencyUtil;
import com.billbuddy.backend.exception.ExpenseNotFoundException;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.exception.InvalidExpenseParticipantException;
import com.billbuddy.backend.exception.InvalidSplitException;
import com.billbuddy.backend.exception.NotExpenseOwnerException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.ExactAmountEntry;
import com.billbuddy.backend.features.expenses.dto.request.ExpenseItemEntry;
import com.billbuddy.backend.features.expenses.dto.request.ItemAssignmentEntry;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.request.PercentageEntry;
import com.billbuddy.backend.features.expenses.dto.request.UpdateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseItemAssignmentResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseItemResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpensePayerResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseSplitResponse;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseItem;
import com.billbuddy.backend.features.expenses.model.ExpenseItemAssignment;
import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.ExpenseItemAssignmentRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseItemRepository;
import com.billbuddy.backend.features.expenses.repository.ExpensePayerRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseSplitRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final ExpenseItemAssignmentRepository expenseItemAssignmentRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpensePayerRepository expensePayerRepository,
            ExpenseSplitRepository expenseSplitRepository,
            ExpenseItemRepository expenseItemRepository,
            ExpenseItemAssignmentRepository expenseItemAssignmentRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService
    ) {
        this.expenseRepository = expenseRepository;
        this.expensePayerRepository = expensePayerRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.expenseItemAssignmentRepository = expenseItemAssignmentRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, Long requesterId, CreateExpenseRequest request) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        User creator = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ExpenseInput input = ExpenseInput.from(request);
        String currency = validateCurrency(input.currency(), group);

        Expense expense = Expense.create(
                group, creator, input.description(), input.amount(), currency,
                input.amount(), BigDecimal.ONE, input.category(), input.receiptUrl(), input.splitType()
        );
        expense = expenseRepository.save(expense);

        persistSplit(expense, group, input);

        return toResponse(expense);
    }

    @Transactional
    public List<ExpenseResponse> listExpenses(Long groupId, Long requesterId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        return expenseRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExpenseResponse getExpense(Long expenseId, Long requesterId) {
        Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
        groupAccessService.requireActiveMember(expense.getGroup().getId(), requesterId);
        return toResponse(expense);
    }

    @Transactional
    public ExpenseResponse updateExpense(Long expenseId, Long requesterId, UpdateExpenseRequest request) {
        Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
        requireOwnerOrAdmin(expense, requesterId);

        Group group = expense.getGroup();
        ExpenseInput input = ExpenseInput.from(request);
        String currency = validateCurrency(input.currency(), group);

        // FK-safe delete order: assignments -> items -> payers/splits
        expenseItemAssignmentRepository.deleteByExpenseItem_Expense_Id(expenseId);
        expenseItemRepository.deleteByExpense_Id(expenseId);
        expensePayerRepository.deleteByExpense_Id(expenseId);
        expenseSplitRepository.deleteByExpense_Id(expenseId);

        expense.update(
                input.description(), input.amount(), currency, input.amount(),
                BigDecimal.ONE, input.category(), input.receiptUrl(), input.splitType()
        );

        persistSplit(expense, group, input);

        return toResponse(expense);
    }

    @Transactional
    public void deleteExpense(Long expenseId, Long requesterId) {
        Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
        requireOwnerOrAdmin(expense, requesterId);
        expense.softDelete();
    }

    // ===================== SPLIT PERSISTENCE =====================

    private void persistSplit(Expense expense, Group group, ExpenseInput input) {
        Set<Long> activeMemberIds = groupMemberRepository.findByGroup_IdAndLeftAtIsNull(group.getId()).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        Set<Long> referencedIds = new HashSet<>();
        input.payers().forEach(p -> referencedIds.add(p.getUserId()));

        switch (input.splitType()) {
            case EQUAL -> {
                if (input.participantUserIds() == null || input.participantUserIds().isEmpty()) {
                    throw new InvalidSplitException("participantUserIds is required for an EQUAL split");
                }
                requireNoDuplicateUserIds(input.participantUserIds(), "participantUserIds");
                referencedIds.addAll(input.participantUserIds());
            }
            case PERCENTAGE -> {
                if (input.percentages() == null || input.percentages().isEmpty()) {
                    throw new InvalidSplitException("percentages is required for a PERCENTAGE split");
                }
                requireNoDuplicateUserIds(input.percentages().stream().map(PercentageEntry::getUserId).toList(), "percentages");
                input.percentages().forEach(p -> referencedIds.add(p.getUserId()));
            }
            case EXACT -> {
                if (input.exactAmounts() == null || input.exactAmounts().isEmpty()) {
                    throw new InvalidSplitException("exactAmounts is required for an EXACT split");
                }
                requireNoDuplicateUserIds(input.exactAmounts().stream().map(ExactAmountEntry::getUserId).toList(), "exactAmounts");
                input.exactAmounts().forEach(e -> referencedIds.add(e.getUserId()));
            }
            case ITEMIZED -> {
                if (input.items() == null || input.items().isEmpty()) {
                    throw new InvalidSplitException("items is required for an ITEMIZED split");
                }
                for (ExpenseItemEntry item : input.items()) {
                    requireNoDuplicateUserIds(
                            item.getAssignments().stream().map(ItemAssignmentEntry::getUserId).toList(),
                            "item assignments"
                    );
                    item.getAssignments().forEach(a -> referencedIds.add(a.getUserId()));
                }
            }
        }

        for (Long id : referencedIds) {
            if (!activeMemberIds.contains(id)) {
                throw new InvalidExpenseParticipantException("User " + id + " is not an active member of this group");
            }
        }

        Map<Long, User> usersById = userRepository.findAllById(referencedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        savePayers(expense, input.payers(), usersById);

        switch (input.splitType()) {
            case EQUAL -> {
                Map<Long, BigDecimal> owed = ExpenseSplitCalculator.equalSplit(
                        expense.getAmount(), input.participantUserIds(), expense.getId()
                );
                saveResolvedSplits(expense, usersById, owed, null);
            }
            case PERCENTAGE -> {
                Map<Long, BigDecimal> pctByUser = input.percentages().stream()
                        .collect(Collectors.toMap(PercentageEntry::getUserId, PercentageEntry::getPercentage));
                Map<Long, BigDecimal> owed = ExpenseSplitCalculator.percentageSplit(
                        expense.getAmount(), pctByUser, expense.getId()
                );
                saveResolvedSplits(expense, usersById, owed, pctByUser);
            }
            case EXACT -> {
                Map<Long, BigDecimal> amountsByUser = input.exactAmounts().stream()
                        .collect(Collectors.toMap(ExactAmountEntry::getUserId, ExactAmountEntry::getAmount));
                Map<Long, BigDecimal> owed = ExpenseSplitCalculator.exactSplit(expense.getAmount(), amountsByUser);
                saveResolvedSplits(expense, usersById, owed, null);
            }
            case ITEMIZED -> saveItemizedSplit(expense, usersById, input.items());
        }
    }

    private void savePayers(Expense expense, List<PayerEntry> payers, Map<Long, User> usersById) {
        BigDecimal sum = payers.stream().map(PayerEntry::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        ExpenseSplitCalculator.requireSumEqualsTotal(expense.getAmount(), sum, "Payer amounts");

        List<ExpensePayer> entities = payers.stream()
                .map(p -> ExpensePayer.create(
                        expense, usersById.get(p.getUserId()), p.getAmountPaid().setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
        expensePayerRepository.saveAll(entities);
    }

    private void saveResolvedSplits(
            Expense expense, Map<Long, User> usersById, Map<Long, BigDecimal> owedByUser, Map<Long, BigDecimal> percentagesByUser
    ) {
        List<ExpenseSplit> splits = owedByUser.entrySet().stream()
                .map(e -> ExpenseSplit.create(
                        expense,
                        usersById.get(e.getKey()),
                        e.getValue(),
                        percentagesByUser == null ? null : percentagesByUser.get(e.getKey())
                ))
                .toList();
        expenseSplitRepository.saveAll(splits);
    }

    private void saveItemizedSplit(Expense expense, Map<Long, User> usersById, List<ExpenseItemEntry> itemEntries) {
        BigDecimal itemAmountSum = itemEntries.stream().map(ExpenseItemEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        ExpenseSplitCalculator.requireSumEqualsTotal(expense.getAmount(), itemAmountSum, "Item amounts");

        Map<Long, BigDecimal> aggregatedOwed = new LinkedHashMap<>();
        for (ExpenseItemEntry itemEntry : itemEntries) {
            ExpenseItem item = ExpenseItem.create(expense, itemEntry.getName(), itemEntry.getAmount());
            item = expenseItemRepository.save(item); // saved first to obtain its id, used as the rotation seed

            Map<Long, BigDecimal> sharesByUser = itemEntry.getAssignments().stream()
                    .collect(Collectors.toMap(ItemAssignmentEntry::getUserId, ItemAssignmentEntry::getShare));
            Map<Long, BigDecimal> perItemOwed = ExpenseSplitCalculator.itemSplit(itemEntry.getAmount(), sharesByUser, item.getId());

            List<ExpenseItemAssignment> assignments = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : sharesByUser.entrySet()) {
                assignments.add(ExpenseItemAssignment.create(item, usersById.get(entry.getKey()), entry.getValue()));
                aggregatedOwed.merge(entry.getKey(), perItemOwed.get(entry.getKey()), BigDecimal::add);
            }
            expenseItemAssignmentRepository.saveAll(assignments);
        }

        saveResolvedSplits(expense, usersById, aggregatedOwed, null);
    }

    // ===================== HELPERS =====================

    private String validateCurrency(String currency, Group group) {
        String normalized = CurrencyUtil.normalize(currency);
        if (!normalized.equals(group.getDefaultCurrency())) {
            throw new InvalidCurrencyException(
                    "Expense currency must match the group's default currency (" + group.getDefaultCurrency() + ")"
            );
        }
        return normalized;
    }

    private void requireOwnerOrAdmin(Expense expense, Long requesterId) {
        GroupMember requesterMembership = groupAccessService.requireActiveMember(expense.getGroup().getId(), requesterId);
        boolean isCreator = expense.getCreatedBy().getId().equals(requesterId);
        boolean isAdmin = requesterMembership.getRole() == GroupRole.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new NotExpenseOwnerException("Only the person who logged this expense or a group admin can modify it");
        }
    }

    private static void requireNoDuplicateUserIds(List<Long> ids, String context) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (!seen.add(id)) {
                throw new InvalidSplitException("Duplicate userId in " + context + ": " + id);
            }
        }
    }

    // ===================== RESPONSE MAPPING =====================

    private ExpenseResponse toResponse(Expense expense) {
        List<ExpensePayerResponse> payers = expensePayerRepository.findByExpense_Id(expense.getId()).stream()
                .map(p -> new ExpensePayerResponse(p.getUser().getId(), p.getUser().getFullName(), p.getAmountPaid()))
                .toList();

        List<ExpenseSplitResponse> splits = expenseSplitRepository.findByExpense_Id(expense.getId()).stream()
                .map(s -> new ExpenseSplitResponse(s.getUser().getId(), s.getUser().getFullName(), s.getAmountOwed(), s.getPercentage()))
                .toList();

        List<ExpenseItemResponse> items = expense.getSplitType() == SplitType.ITEMIZED
                ? buildItemResponses(expense)
                : List.of();

        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getConvertedAmount(),
                expense.getExchangeRate(),
                expense.getCategory(),
                expense.getReceiptUrl(),
                expense.getSplitType(),
                expense.getCreatedBy().getId(),
                expense.getCreatedBy().getFullName(),
                payers,
                splits,
                items,
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }

    private List<ExpenseItemResponse> buildItemResponses(Expense expense) {
        List<ExpenseItem> items = expenseItemRepository.findByExpense_Id(expense.getId());
        List<ExpenseItemResponse> result = new ArrayList<>();

        for (ExpenseItem item : items) {
            List<ExpenseItemAssignment> assignments = expenseItemAssignmentRepository.findByExpenseItem_Id(item.getId());
            Map<Long, BigDecimal> sharesByUser = assignments.stream()
                    .collect(Collectors.toMap(a -> a.getUser().getId(), ExpenseItemAssignment::getShare));
            Map<Long, BigDecimal> perItemOwed = ExpenseSplitCalculator.itemSplit(item.getAmount(), sharesByUser, item.getId());

            List<ExpenseItemAssignmentResponse> assignmentResponses = assignments.stream()
                    .map(a -> new ExpenseItemAssignmentResponse(
                            a.getUser().getId(), a.getUser().getFullName(), a.getShare(), perItemOwed.get(a.getUser().getId())
                    ))
                    .toList();

            result.add(new ExpenseItemResponse(item.getId(), item.getName(), item.getAmount(), assignmentResponses));
        }
        return result;
    }

    // ===================== INTERNAL INPUT SHAPE =====================
    // Bridges CreateExpenseRequest/UpdateExpenseRequest (identical shape, separate classes
    // per this codebase's Create/Update DTO convention) into one type the logic above shares.

    private record ExpenseInput(
            String description,
            BigDecimal amount,
            String currency,
            String category,
            String receiptUrl,
            SplitType splitType,
            List<PayerEntry> payers,
            List<Long> participantUserIds,
            List<PercentageEntry> percentages,
            List<ExactAmountEntry> exactAmounts,
            List<ExpenseItemEntry> items
    ) {
        static ExpenseInput from(CreateExpenseRequest r) {
            return new ExpenseInput(
                    r.getDescription(), r.getAmount(), r.getCurrency(), r.getCategory(), r.getReceiptUrl(),
                    r.getSplitType(), r.getPayers(), r.getParticipantUserIds(), r.getPercentages(),
                    r.getExactAmounts(), r.getItems()
            );
        }

        static ExpenseInput from(UpdateExpenseRequest r) {
            return new ExpenseInput(
                    r.getDescription(), r.getAmount(), r.getCurrency(), r.getCategory(), r.getReceiptUrl(),
                    r.getSplitType(), r.getPayers(), r.getParticipantUserIds(), r.getPercentages(),
                    r.getExactAmounts(), r.getItems()
            );
        }
    }
}
