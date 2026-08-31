package com.billbuddy.backend.features.settlements.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.ExpensePayerRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseSplitRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.settlements.dto.response.BalanceResponse;
import com.billbuddy.backend.features.settlements.dto.response.SimplifiedSettlementResponse;
import com.billbuddy.backend.features.settlements.model.Settlement;
import com.billbuddy.backend.features.settlements.repository.SettlementRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @Mock
    private ExpensePayerRepository expensePayerRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private BalanceService balanceService;

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

    private Expense buildExpense(Long id, Group group, User createdBy, BigDecimal amount) {
        Expense expense = Expense.create(
                group, createdBy, "Dinner", amount, "INR", amount, BigDecimal.ONE, null, null, SplitType.EQUAL
        );
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }

    private void stubGroup(Long groupId, Long requesterId, Group group, GroupMember membership) {
        when(groupAccessService.requireActiveMember(groupId, requesterId)).thenReturn(membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(groupId)).thenReturn(Optional.of(group));
    }

    // ===================== RAW BALANCES =====================

    @Test
    void getBalances_seedsActiveMembersAtZero_whenNoActivity() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        stubGroup(10L, 1L, group, adminMembership);
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L))
                .thenReturn(List.of(adminMembership, GroupMember.createMember(group, member1)));
        when(expensePayerRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L)).thenReturn(List.of());
        when(expenseSplitRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L)).thenReturn(List.of());
        when(settlementRepository.findByGroup_IdAndDeletedAtIsNull(10L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(admin, member1));

        List<BalanceResponse> result = balanceService.getBalances(10L, 1L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(b -> assertThat(b.getNetBalance()).isEqualByComparingTo("0.00"));
    }

    @Test
    void getBalances_computesNetCorrectly_fromPayersSplitsAndSettlements() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("300.00"));

        stubGroup(10L, 1L, group, adminMembership);
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(
                adminMembership, GroupMember.createMember(group, member1), GroupMember.createMember(group, member2)
        ));

        // admin paid 300, split equally 3 ways (100 each)
        when(expensePayerRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(ExpensePayer.create(expense, admin, new BigDecimal("300.00"))));
        when(expenseSplitRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(
                        ExpenseSplit.create(expense, admin, new BigDecimal("100.00"), null),
                        ExpenseSplit.create(expense, member1, new BigDecimal("100.00"), null),
                        ExpenseSplit.create(expense, member2, new BigDecimal("100.00"), null)
                ));

        // member1 pays admin 100 in cash -- fully settles member1's debt
        Settlement settlement = Settlement.create(group, member1, admin, member1, new BigDecimal("100.00"), "INR", null);
        when(settlementRepository.findByGroup_IdAndDeletedAtIsNull(10L)).thenReturn(List.of(settlement));

        when(userRepository.findAllById(any())).thenReturn(List.of(admin, member1, member2));

        List<BalanceResponse> result = balanceService.getBalances(10L, 1L);

        assertThat(netFor(result, 1L)).isEqualByComparingTo("100.00");
        assertThat(netFor(result, 2L)).isEqualByComparingTo("0.00");
        assertThat(netFor(result, 3L)).isEqualByComparingTo("-100.00");
    }

    @Test
    void getBalances_includesFormerMember_whoHasHistoricalActivity() {
        User admin = buildUser(1L);
        User formerMember = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("50.00"));

        stubGroup(10L, 1L, group, adminMembership);
        // only admin is still an active member -- formerMember already left
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(adminMembership));
        when(expensePayerRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(ExpensePayer.create(expense, admin, new BigDecimal("50.00"))));
        when(expenseSplitRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(ExpenseSplit.create(expense, formerMember, new BigDecimal("50.00"), null)));
        when(settlementRepository.findByGroup_IdAndDeletedAtIsNull(10L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(admin, formerMember));

        List<BalanceResponse> result = balanceService.getBalances(10L, 1L);

        assertThat(result).hasSize(2);
        assertThat(netFor(result, 2L)).isEqualByComparingTo("-50.00");
    }

    @Test
    void getBalances_throwsGroupNotFound_whenGroupMissing() {
        User admin = buildUser(1L);
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(null, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalances(10L, 1L))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ===================== SIMPLIFIED BALANCES =====================

    @Test
    void getSimplifiedBalances_returnsMinimalTransferSet() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("50.00"));

        stubGroup(10L, 1L, group, adminMembership);
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L))
                .thenReturn(List.of(adminMembership, GroupMember.createMember(group, member1)));
        when(expensePayerRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(ExpensePayer.create(expense, admin, new BigDecimal("50.00"))));
        when(expenseSplitRepository.findByExpense_Group_IdAndExpense_DeletedAtIsNull(10L))
                .thenReturn(List.of(
                        ExpenseSplit.create(expense, admin, new BigDecimal("25.00"), null),
                        ExpenseSplit.create(expense, member1, new BigDecimal("25.00"), null)
                ));
        when(settlementRepository.findByGroup_IdAndDeletedAtIsNull(10L)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(admin, member1));

        List<SimplifiedSettlementResponse> result = balanceService.getSimplifiedBalances(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromUserId()).isEqualTo(2L);
        assertThat(result.get(0).getToUserId()).isEqualTo(1L);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void getSimplifiedBalances_throwsGroupNotFound_whenGroupMissing() {
        User admin = buildUser(1L);
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(null, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getSimplifiedBalances(10L, 1L))
                .isInstanceOf(GroupNotFoundException.class);
    }

    private BigDecimal netFor(List<BalanceResponse> balances, Long userId) {
        return balances.stream()
                .filter(b -> b.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No balance found for userId " + userId))
                .getNetBalance();
    }
}
