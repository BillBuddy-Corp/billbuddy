package com.billbuddy.backend.features.settlements.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
import com.billbuddy.backend.features.expenses.repository.ExpensePayerRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseSplitRepository;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.settlements.dto.response.BalanceResponse;
import com.billbuddy.backend.features.settlements.dto.response.SimplifiedSettlementResponse;
import com.billbuddy.backend.features.settlements.model.Settlement;
import com.billbuddy.backend.features.settlements.repository.SettlementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;
    private final ExpensePayerRepository expensePayerRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;

    public BalanceService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService,
            ExpensePayerRepository expensePayerRepository,
            ExpenseSplitRepository expenseSplitRepository,
            SettlementRepository settlementRepository
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
        this.expensePayerRepository = expensePayerRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional
    public List<BalanceResponse> getBalances(Long groupId, Long requesterId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        requireGroupExists(groupId);

        Map<Long, BigDecimal> net = computeNetBalances(groupId);
        Map<Long, User> usersById = userRepository.findAllById(net.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return net.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new BalanceResponse(e.getKey(), usersById.get(e.getKey()).getFullName(), e.getValue()))
                .toList();
    }

    // No access check here, unlike the other public methods -- this is meant to be called by
    // GroupService as an internal business-rule check (blocking group deletion) after it has
    // already authorized the caller itself, not exposed as its own endpoint.
    @Transactional
    public boolean hasUnsettledBalances(Long groupId) {
        return computeNetBalances(groupId).values().stream()
                .anyMatch(balance -> balance.compareTo(BigDecimal.ZERO) != 0);
    }

    @Transactional
    public List<SimplifiedSettlementResponse> getSimplifiedBalances(Long groupId, Long requesterId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        requireGroupExists(groupId);

        Map<Long, BigDecimal> net = computeNetBalances(groupId);
        List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(net);

        Map<Long, User> usersById = userRepository.findAllById(net.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return transfers.stream()
                .map(t -> new SimplifiedSettlementResponse(
                        t.fromUserId(), usersById.get(t.fromUserId()).getFullName(),
                        t.toUserId(), usersById.get(t.toUserId()).getFullName(),
                        t.amount()
                ))
                .toList();
    }

    // ===================== HELPERS =====================

    private void requireGroupExists(Long groupId) {
        groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
    }

    // Seeds every active member at zero (so members with no activity still appear), then folds in
    // every expense payer/split/settlement row for the group. A user who has left the group but
    // has historical activity still shows up here, via Map.merge's put-if-absent behavior --
    // intentional, since a real debt doesn't disappear when someone leaves.
    private Map<Long, BigDecimal> computeNetBalances(Long groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroup_IdAndLeftAtIsNull(groupId);

        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        for (GroupMember member : members) {
            net.put(member.getUser().getId(), BigDecimal.ZERO);
        }

        // Map.merge only calls the remapping function when the key is already present -- for an
        // absent key it just inserts the given value as-is. So every contribution here is
        // expressed as a value to ADD (negated up front where it should reduce the balance),
        // never as a subtraction, or a first-touch merge on a non-seeded user would silently
        // insert the wrong sign.
        for (ExpensePayer payer : expensePayerRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(groupId)) {
            net.merge(payer.getUser().getId(), payer.getAmountPaid(), BigDecimal::add);
        }
        for (ExpenseSplit split : expenseSplitRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(groupId)) {
            net.merge(split.getUser().getId(), split.getAmountOwed().negate(), BigDecimal::add);
        }
        for (Settlement settlement : settlementRepository.findByGroup_IdAndDeletedAtIsNull(groupId)) {
            net.merge(settlement.getPaidBy().getId(), settlement.getAmount(), BigDecimal::add);
            net.merge(settlement.getPaidTo().getId(), settlement.getAmount().negate(), BigDecimal::add);
        }

        net.replaceAll((userId, amount) -> amount.setScale(2, RoundingMode.HALF_UP));
        return net;
    }
}
