package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.common.CurrencyUtil;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.exception.InvalidRecurrenceException;
import com.billbuddy.backend.exception.InvalidSplitException;
import com.billbuddy.backend.exception.NotRecurringExpenseOwnerException;
import com.billbuddy.backend.exception.RecurringExpenseNotFoundException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.CreateRecurringExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.ExactAmountEntry;
import com.billbuddy.backend.features.expenses.dto.request.ExpenseItemEntry;
import com.billbuddy.backend.features.expenses.dto.request.ItemAssignmentEntry;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.request.PercentageEntry;
import com.billbuddy.backend.features.expenses.dto.response.RecurringExpenseResponse;
import com.billbuddy.backend.features.expenses.model.RecurringExpenseTemplate;
import com.billbuddy.backend.features.expenses.model.RecurringFrequency;
import com.billbuddy.backend.features.expenses.repository.RecurringExpenseTemplateRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecurringExpenseService {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;
    private final ExpenseService expenseService;
    private final ObjectMapper objectMapper;

    public RecurringExpenseService(
            RecurringExpenseTemplateRepository templateRepository,
            GroupRepository groupRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService,
            ExpenseService expenseService,
            ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
        this.expenseService = expenseService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecurringExpenseResponse createTemplate(Long groupId, Long requesterId, CreateRecurringExpenseRequest request) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        User creator = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String currency = CurrencyUtil.normalize(request.getCurrency());
        BigDecimal exchangeRate = request.getExchangeRate();
        if (!currency.equals(group.getDefaultCurrency()) && (exchangeRate == null || exchangeRate.signum() <= 0)) {
            throw new InvalidCurrencyException(
                    "Expense currency (" + currency + ") differs from the group's default currency ("
                            + group.getDefaultCurrency() + "), a positive exchangeRate is required"
            );
        }

        validateRecurrenceFields(request.getFrequency(), request.getDayOfWeek(), request.getDayOfMonth());
        validateSplitMath(request);

        SplitConfig splitConfig = new SplitConfig(
                request.getPayers(), request.getParticipantUserIds(), request.getPercentages(),
                request.getExactAmounts(), request.getItems()
        );
        String splitConfigJson = writeSplitConfig(splitConfig);

        LocalDate nextRunAt = computeNextRun(request.getFrequency(), request.getDayOfWeek(), request.getDayOfMonth(), LocalDate.now());

        RecurringExpenseTemplate template = RecurringExpenseTemplate.create(
                group, creator, request.getDescription(), request.getAmount(), currency, exchangeRate,
                request.getCategory(), request.getSplitType(), request.getFrequency(),
                request.getDayOfWeek(), request.getDayOfMonth(), splitConfigJson, nextRunAt
        );
        template = templateRepository.save(template);

        return toResponse(template);
    }

    @Transactional
    public List<RecurringExpenseResponse> listTemplates(Long groupId, Long requesterId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        return templateRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void pauseTemplate(Long templateId, Long requesterId) {
        RecurringExpenseTemplate template = requireTemplate(templateId);
        requireOwnerOrAdmin(template, requesterId);
        template.pause();
    }

    @Transactional
    public void resumeTemplate(Long templateId, Long requesterId) {
        RecurringExpenseTemplate template = requireTemplate(templateId);
        requireOwnerOrAdmin(template, requesterId);
        template.resume();
    }

    @Transactional
    public void cancelTemplate(Long templateId, Long requesterId) {
        RecurringExpenseTemplate template = requireTemplate(templateId);
        requireOwnerOrAdmin(template, requesterId);
        template.cancel();
    }

    // ===================== GENERATION =====================

    // Plain (no ambient transaction) so a failure inside expenseService.createExpense -- its own
    // separately-transactional call -- can't mark a shared transaction rollback-only and silently
    // discard the nextRunAt advance below. Same rollback-poisoning pitfall as the OTP attempt
    // counter: keep the two units of work in genuinely separate transactions.
    public void runDueGenerations() {
        List<Long> dueTemplateIds = templateRepository.findDueTemplates().stream()
                .map(RecurringExpenseTemplate::getId)
                .toList();
        for (Long templateId : dueTemplateIds) {
            try {
                processDueTemplate(templateId);
            } catch (Exception ex) {
                log.error("Unexpected failure processing recurring expense template {}", templateId, ex);
            }
        }
    }

    void processDueTemplate(Long templateId) {
        RecurringExpenseTemplate template = templateRepository.findByIdAndDeletedAtIsNull(templateId).orElse(null);
        if (template == null) {
            return;
        }

        try {
            CreateExpenseRequest request = buildExpenseRequest(template);
            expenseService.createExpense(template.getGroup().getId(), template.getCreatedBy().getId(), request);
        } catch (Exception ex) {
            log.error("Skipping this cycle for recurring expense template {}, generation failed", templateId, ex);
        }

        LocalDate next = computeNextRun(template.getFrequency(), template.getDayOfWeek(), template.getDayOfMonth(), template.getNextRunAt());
        advanceNextRun(templateId, next);
    }

    // processDueTemplate calls this on `this` (same-bean self-invocation), which bypasses the
    // @Transactional proxy on this method entirely -- so the entity loaded below is detached the
    // moment findByIdAndDeletedAtIsNull's own short-lived transaction closes, and mutating it would
    // silently never be flushed by dirty-checking alone. An explicit save() sidesteps that: it
    // persists through the repository's own transactional proxy (a genuinely different bean),
    // regardless of whether this method's own @Transactional took effect.
    void advanceNextRun(Long templateId, LocalDate next) {
        templateRepository.findByIdAndDeletedAtIsNull(templateId).ifPresent(template -> {
            template.advanceNextRun(next);
            templateRepository.save(template);
        });
    }

    // ===================== HELPERS =====================

    LocalDate computeNextRun(RecurringFrequency frequency, Integer dayOfWeek, Integer dayOfMonth, LocalDate from) {
        if (frequency == RecurringFrequency.WEEKLY) {
            DayOfWeek target = DayOfWeek.of(dayOfWeek);
            LocalDate next = from.plusDays(1);
            while (next.getDayOfWeek() != target) {
                next = next.plusDays(1);
            }
            return next;
        }

        LocalDate firstOfNextMonth = from.plusMonths(1).withDayOfMonth(1);
        int clampedDay = Math.min(dayOfMonth, firstOfNextMonth.lengthOfMonth());
        return firstOfNextMonth.withDayOfMonth(clampedDay);
    }

    private void validateRecurrenceFields(RecurringFrequency frequency, Integer dayOfWeek, Integer dayOfMonth) {
        if (frequency == RecurringFrequency.WEEKLY && dayOfWeek == null) {
            throw new InvalidRecurrenceException("dayOfWeek is required for a WEEKLY recurrence");
        }
        if (frequency == RecurringFrequency.MONTHLY && dayOfMonth == null) {
            throw new InvalidRecurrenceException("dayOfMonth is required for a MONTHLY recurrence");
        }
    }

    // Structural split-math validation only (sums reconcile, no duplicate ids) -- reuses the same
    // pure ExpenseSplitCalculator functions ExpenseService itself uses, discarding the result.
    // Deliberately skips active-group-membership checks: that's re-validated for real on every
    // generation anyway via expenseService.createExpense, and membership can legitimately drift
    // over a template's lifetime, so re-checking it here would only be a stale guarantee.
    private void validateSplitMath(CreateRecurringExpenseRequest request) {
        BigDecimal payerSum = request.getPayers().stream().map(PayerEntry::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        ExpenseSplitCalculator.requireSumEqualsTotal(request.getAmount(), payerSum, "Payer amounts");
        requireNoDuplicateUserIds(request.getPayers().stream().map(PayerEntry::getUserId).toList());

        switch (request.getSplitType()) {
            case EQUAL -> {
                requireNoDuplicateUserIds(request.getParticipantUserIds());
                ExpenseSplitCalculator.equalSplit(request.getAmount(), request.getParticipantUserIds(), 0L);
            }
            case PERCENTAGE -> {
                if (request.getPercentages() == null || request.getPercentages().isEmpty()) {
                    throw new InvalidSplitException("percentages is required for a PERCENTAGE split");
                }
                requireNoDuplicateUserIds(request.getPercentages().stream().map(PercentageEntry::getUserId).toList());
                Map<Long, BigDecimal> pctByUser = request.getPercentages().stream()
                        .collect(Collectors.toMap(PercentageEntry::getUserId, PercentageEntry::getPercentage));
                ExpenseSplitCalculator.percentageSplit(request.getAmount(), pctByUser, 0L);
            }
            case EXACT -> {
                if (request.getExactAmounts() == null || request.getExactAmounts().isEmpty()) {
                    throw new InvalidSplitException("exactAmounts is required for an EXACT split");
                }
                requireNoDuplicateUserIds(request.getExactAmounts().stream().map(ExactAmountEntry::getUserId).toList());
                Map<Long, BigDecimal> amountsByUser = request.getExactAmounts().stream()
                        .collect(Collectors.toMap(ExactAmountEntry::getUserId, ExactAmountEntry::getAmount));
                ExpenseSplitCalculator.exactSplit(request.getAmount(), amountsByUser);
            }
            case ITEMIZED -> {
                if (request.getItems() == null || request.getItems().isEmpty()) {
                    throw new InvalidSplitException("items is required for an ITEMIZED split");
                }
                BigDecimal itemAmountSum = request.getItems().stream().map(ExpenseItemEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                ExpenseSplitCalculator.requireSumEqualsTotal(request.getAmount(), itemAmountSum, "Item amounts");
                for (ExpenseItemEntry item : request.getItems()) {
                    requireNoDuplicateUserIds(item.getAssignments().stream().map(ItemAssignmentEntry::getUserId).toList());
                    Map<Long, BigDecimal> sharesByUser = item.getAssignments().stream()
                            .collect(Collectors.toMap(ItemAssignmentEntry::getUserId, ItemAssignmentEntry::getShare));
                    ExpenseSplitCalculator.itemSplit(item.getAmount(), sharesByUser, 0L);
                }
            }
        }
    }

    private void requireNoDuplicateUserIds(List<Long> userIds) {
        if (userIds == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : userIds) {
            if (!seen.add(id)) {
                throw new InvalidSplitException("Duplicate user id in split: " + id);
            }
        }
    }

    private RecurringExpenseTemplate requireTemplate(Long templateId) {
        return templateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException("Recurring expense not found"));
    }

    private void requireOwnerOrAdmin(RecurringExpenseTemplate template, Long requesterId) {
        GroupMember requesterMembership = groupAccessService.requireActiveMember(template.getGroup().getId(), requesterId);
        boolean isCreator = template.getCreatedBy().getId().equals(requesterId);
        boolean isAdmin = requesterMembership.getRole() == GroupRole.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new NotRecurringExpenseOwnerException("Only the person who created this recurring expense or a group admin can modify it");
        }
    }

    private CreateExpenseRequest buildExpenseRequest(RecurringExpenseTemplate template) {
        SplitConfig splitConfig = readSplitConfig(template.getSplitConfig());

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription(template.getDescription());
        request.setAmount(template.getAmount());
        request.setCurrency(template.getCurrency());
        request.setExchangeRate(template.getExchangeRate());
        request.setCategory(template.getCategory());
        request.setSplitType(template.getSplitType());
        request.setPayers(splitConfig.getPayers());
        request.setParticipantUserIds(splitConfig.getParticipantUserIds());
        request.setPercentages(splitConfig.getPercentages());
        request.setExactAmounts(splitConfig.getExactAmounts());
        request.setItems(splitConfig.getItems());
        return request;
    }

    private String writeSplitConfig(SplitConfig splitConfig) {
        try {
            return objectMapper.writeValueAsString(splitConfig);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize recurring expense split configuration", ex);
        }
    }

    private SplitConfig readSplitConfig(String json) {
        try {
            return objectMapper.readValue(json, SplitConfig.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse stored recurring expense split configuration", ex);
        }
    }

    private RecurringExpenseResponse toResponse(RecurringExpenseTemplate template) {
        SplitConfig splitConfig = readSplitConfig(template.getSplitConfig());
        return new RecurringExpenseResponse(
                template.getId(),
                template.getGroup().getId(),
                template.getDescription(),
                template.getAmount(),
                template.getCurrency(),
                template.getExchangeRate(),
                template.getCategory(),
                template.getSplitType(),
                splitConfig.getPayers(),
                splitConfig.getParticipantUserIds(),
                splitConfig.getPercentages(),
                splitConfig.getExactAmounts(),
                splitConfig.getItems(),
                template.getFrequency(),
                template.getDayOfWeek(),
                template.getDayOfMonth(),
                template.getNextRunAt(),
                template.isActive(),
                template.getCreatedBy().getId(),
                template.getCreatedBy().getFullName(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SplitConfig {
        private List<PayerEntry> payers;
        private List<Long> participantUserIds;
        private List<PercentageEntry> percentages;
        private List<ExactAmountEntry> exactAmounts;
        private List<ExpenseItemEntry> items;
    }
}
