package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.ExpenseNotFoundException;
import com.billbuddy.backend.exception.FriendshipNotFoundException;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.exception.InvalidExpenseParticipantException;
import com.billbuddy.backend.exception.InvalidSplitException;
import com.billbuddy.backend.exception.NotExpenseOwnerException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.friends.service.FriendshipService;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.ExpenseItemEntry;
import com.billbuddy.backend.features.expenses.dto.request.ItemAssignmentEntry;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.request.UpdateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.response.ExchangeRateResponse;
import com.billbuddy.backend.features.expenses.dto.response.ExpenseResponse;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
import com.billbuddy.backend.features.expenses.model.ExpenseItem;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.ExpenseItemAssignmentRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseItemRepository;
import com.billbuddy.backend.features.expenses.repository.ExpensePayerRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseRepository;
import com.billbuddy.backend.features.expenses.repository.ExpenseSplitRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpensePayerRepository expensePayerRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private ExpenseItemRepository expenseItemRepository;

    @Mock
    private ExpenseItemAssignmentRepository expenseItemAssignmentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private FriendshipService friendshipService;

    @InjectMocks
    private ExpenseService expenseService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator, String currency) {
        Group group = Group.create("Goa Trip", "desc", currency, creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private Expense buildExpense(Long id, Group group, User createdBy, BigDecimal amount, SplitType splitType) {
        Expense expense = Expense.create(
                group, createdBy, "Dinner", amount, group.getDefaultCurrency(),
                amount, BigDecimal.ONE, null, null, splitType
        );
        ReflectionTestUtils.setField(expense, "id", id);
        return expense;
    }

    private void stubActiveMembers(Long groupId, User... users) {
        List<GroupMember> members = List.of(users).stream()
                .map(u -> GroupMember.createMember(null, u))
                .toList();
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(groupId)).thenReturn(members);
        when(userRepository.findAllById(any())).thenReturn(List.of(users));
    }

    // ===================== CREATE =====================

    @Test
    void createExpense_savesEqualSplit_whenValid() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });
        stubActiveMembers(10L, admin, member1, member2);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L, 2L, 3L));

        expenseService.createExpense(10L, 1L, request);

        ArgumentCaptor<List<com.billbuddy.backend.features.expenses.model.ExpenseSplit>> splitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
        BigDecimal sum = splitsCaptor.getValue().stream()
                .map(com.billbuddy.backend.features.expenses.model.ExpenseSplit::getAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("90.00");

        verify(expensePayerRepository).saveAll(any());
        verify(notificationService).notifyExpenseCreated(any(Expense.class), eq(1L));
    }

    @Test
    void createExpense_throwsGroupNotFound_whenGroupMissing() {
        GroupMember membership = GroupMember.createMember(null, buildUser(1L));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setSplitType(SplitType.EQUAL);

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void createExpense_throwsInvalidCurrency_whenCurrencyMismatchesGroup() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("USD");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(InvalidCurrencyException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpense_succeedsWithDifferentCurrency_whenRateSupplied() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });
        stubActiveMembers(10L, admin);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Bangkok dinner");
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("THB");
        request.setExchangeRate(new BigDecimal("2.5"));
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("100"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        ExpenseResponse response = expenseService.createExpense(10L, 1L, request);

        assertThat(response.getCurrency()).isEqualTo("THB");
        assertThat(response.getAmount()).isEqualByComparingTo("100");
        assertThat(response.getExchangeRate()).isEqualByComparingTo("2.5");
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("250.00");

        ArgumentCaptor<List<ExpensePayer>> payersCaptor = ArgumentCaptor.forClass(List.class);
        verify(expensePayerRepository).saveAll(payersCaptor.capture());
        assertThat(payersCaptor.getValue().get(0).getAmountPaid()).isEqualByComparingTo("250.00");

        ArgumentCaptor<List<ExpenseSplit>> splitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
        assertThat(splitsCaptor.getValue().get(0).getAmountOwed()).isEqualByComparingTo("250.00");

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void createExpense_throwsInvalidCurrency_whenDifferentCurrencyAndNoRateSupplied() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Bangkok dinner");
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("THB");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("100"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(InvalidCurrencyException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpense_throwsInvalidCurrency_whenRateNotPositive() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Bangkok dinner");
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("THB");
        request.setExchangeRate(BigDecimal.ZERO);
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("100"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(InvalidCurrencyException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpense_throwsInvalidExpenseParticipant_whenParticipantNotActiveMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });
        // only admin is an active member -- userId 99 is not
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L))
                .thenReturn(List.of(GroupMember.createAdmin(group, admin)));

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L, 99L));

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(InvalidExpenseParticipantException.class);
    }

    @Test
    void createExpense_throwsInvalidSplit_whenPayerSumMismatch() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });
        stubActiveMembers(10L, admin);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("50")); // doesn't match total of 90
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        assertThatThrownBy(() -> expenseService.createExpense(10L, 1L, request))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void createExpense_savesItemizedSplit_whenValid() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });
        when(expenseItemRepository.save(any(ExpenseItem.class))).thenAnswer(inv -> {
            ExpenseItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 500L);
            return item;
        });
        stubActiveMembers(10L, admin, member1);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Restaurant");
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.ITEMIZED);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("100"));
        request.setPayers(List.of(payer));

        ExpenseItemEntry item = new ExpenseItemEntry();
        item.setName("Pizza");
        item.setAmount(new BigDecimal("100"));
        ItemAssignmentEntry a1 = new ItemAssignmentEntry();
        a1.setUserId(1L);
        a1.setShare(BigDecimal.ONE);
        ItemAssignmentEntry a2 = new ItemAssignmentEntry();
        a2.setUserId(2L);
        a2.setShare(BigDecimal.ONE);
        item.setAssignments(List.of(a1, a2));
        request.setItems(List.of(item));

        expenseService.createExpense(10L, 1L, request);

        verify(expenseItemRepository).save(any(ExpenseItem.class));
        verify(expenseItemAssignmentRepository).saveAll(any());

        ArgumentCaptor<List<com.billbuddy.backend.features.expenses.model.ExpenseSplit>> splitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
        BigDecimal sum = splitsCaptor.getValue().stream()
                .map(com.billbuddy.backend.features.expenses.model.ExpenseSplit::getAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    // ===================== GET / LIST =====================

    @Test
    void getExpense_returnsResponse_whenActiveMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(expensePayerRepository.findByExpense_Id(100L)).thenReturn(List.of());
        when(expenseSplitRepository.findByExpense_Id(100L)).thenReturn(List.of());

        ExpenseResponse response = expenseService.getExpense(100L, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getGroupId()).isEqualTo(10L);
    }

    @Test
    void getExpense_throwsExpenseNotFound_whenMissing() {
        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.getExpense(100L, 1L))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void listExpenses_returnsMappedExpenses() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("90"), SplitType.EQUAL);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(expenseRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of(expense));
        when(expensePayerRepository.findByExpense_Id(100L)).thenReturn(List.of());
        when(expenseSplitRepository.findByExpense_Id(100L)).thenReturn(List.of());

        List<ExpenseResponse> result = expenseService.listExpenses(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    // ===================== UPDATE =====================

    @Test
    void updateExpense_succeeds_whenCreator() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        stubActiveMembers(10L, admin);

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Dinner (updated)");
        request.setAmount(new BigDecimal("50"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("50"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        ExpenseResponse response = expenseService.updateExpense(100L, 1L, request);

        assertThat(response.getDescription()).isEqualTo("Dinner (updated)");
        assertThat(expense.getAmount()).isEqualByComparingTo("50");
        verify(expenseSplitRepository).deleteByExpense_Id(100L);
        verify(expensePayerRepository).deleteByExpense_Id(100L);
        verify(expenseItemRepository).deleteByExpense_Id(100L);
        verify(expenseItemAssignmentRepository).deleteByExpenseItem_Expense_Id(100L);
    }

    @Test
    void updateExpense_succeeds_whenGroupAdminButNotCreator() {
        User creator = buildUser(1L);
        User admin = buildUser(2L);
        Group group = buildGroup(10L, creator, "INR");
        Expense expense = buildExpense(100L, group, creator, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createAdmin(group, admin));
        stubActiveMembers(10L, creator);

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Dinner (updated by admin)");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));

        ExpenseResponse response = expenseService.updateExpense(100L, 2L, request);

        assertThat(response.getDescription()).isEqualTo("Dinner (updated by admin)");
    }

    @Test
    void updateExpense_throwsNotExpenseOwner_whenNeitherCreatorNorAdmin() {
        User creator = buildUser(1L);
        User other = buildUser(2L);
        Group group = buildGroup(10L, creator, "INR");
        Expense expense = buildExpense(100L, group, creator, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, other));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setSplitType(SplitType.EQUAL);

        assertThatThrownBy(() -> expenseService.updateExpense(100L, 2L, request))
                .isInstanceOf(NotExpenseOwnerException.class);

        verify(expenseSplitRepository, never()).deleteByExpense_Id(any());
    }

    // ===================== DELETE =====================

    @Test
    void deleteExpense_softDeletes_whenCreator() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        Expense expense = buildExpense(100L, group, admin, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));

        expenseService.deleteExpense(100L, 1L);

        assertThat(expense.isDeleted()).isTrue();
    }

    @Test
    void deleteExpense_throwsNotExpenseOwner_whenNeitherCreatorNorAdmin() {
        User creator = buildUser(1L);
        User other = buildUser(2L);
        Group group = buildGroup(10L, creator, "INR");
        Expense expense = buildExpense(100L, group, creator, new BigDecimal("90"), SplitType.EQUAL);

        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(expense));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, other));

        assertThatThrownBy(() -> expenseService.deleteExpense(100L, 2L))
                .isInstanceOf(NotExpenseOwnerException.class);

        assertThat(expense.isDeleted()).isFalse();
    }

    @Test
    void deleteExpense_throwsExpenseNotFound_whenMissing() {
        when(expenseRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.deleteExpense(100L, 1L))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    // ===================== SUGGEST EXCHANGE RATE =====================

    @Test
    void suggestExchangeRate_shortCircuitsToOne_whenSameCurrency() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));

        ExchangeRateResponse response = expenseService.suggestExchangeRate(10L, 1L, "inr");

        assertThat(response.getFromCurrency()).isEqualTo("INR");
        assertThat(response.getToCurrency()).isEqualTo("INR");
        assertThat(response.getRate()).isEqualByComparingTo("1");
        assertThat(response.getAsOf()).isEqualTo(LocalDate.now());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void suggestExchangeRate_delegatesToProvider_whenDifferentCurrency() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        LocalDate asOf = LocalDate.of(2026, 9, 4);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(exchangeRateService.getRate("THB", "INR"))
                .thenReturn(new ExchangeRateService.ExchangeRateQuote(new BigDecimal("2.51"), asOf));

        ExchangeRateResponse response = expenseService.suggestExchangeRate(10L, 1L, "thb");

        assertThat(response.getFromCurrency()).isEqualTo("THB");
        assertThat(response.getToCurrency()).isEqualTo("INR");
        assertThat(response.getRate()).isEqualByComparingTo("2.51");
        assertThat(response.getAsOf()).isEqualTo(asOf);
    }

    @Test
    void suggestExchangeRate_throwsGroupNotFound_whenGroupMissing() {
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createMember(null, buildUser(1L)));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.suggestExchangeRate(10L, 1L, "THB"))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ===================== FRIEND (NON-GROUP) EXPENSES =====================

    @Test
    void createFriendExpense_savesEqualSplit_whenValid() {
        User user = buildUser(1L);
        User friend = buildUser(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(userRepository.findAllById(any())).thenReturn(List.of(user, friend));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 200L);
            return e;
        });

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Coffee");
        request.setAmount(new BigDecimal("20"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("20"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L, 2L));

        ExpenseResponse response = expenseService.createFriendExpense(1L, 2L, request);

        assertThat(response.getGroupId()).isNull();
        verify(friendshipService).requireFriends(1L, 2L);

        ArgumentCaptor<List<com.billbuddy.backend.features.expenses.model.ExpenseSplit>> splitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
        BigDecimal sum = splitsCaptor.getValue().stream()
                .map(com.billbuddy.backend.features.expenses.model.ExpenseSplit::getAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("20.00");
        verifyNoInteractions(notificationService);
    }

    @Test
    void createFriendExpense_throwsFriendshipNotFound_whenNotFriends() {
        doThrow(new FriendshipNotFoundException("You are not friends with this user"))
                .when(friendshipService).requireFriends(1L, 2L);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Coffee");
        request.setAmount(new BigDecimal("20"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("20"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L, 2L));

        assertThatThrownBy(() -> expenseService.createFriendExpense(1L, 2L, request))
                .isInstanceOf(FriendshipNotFoundException.class);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createFriendExpense_throwsInvalidExpenseParticipant_whenThirdPartyReferenced() {
        User user = buildUser(1L);
        User friend = buildUser(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 200L);
            return e;
        });

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Coffee");
        request.setAmount(new BigDecimal("30"));
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("30"));
        request.setPayers(List.of(payer));
        // 3L isn't a friend, only 1L and 2L are valid participants for a friend expense
        request.setParticipantUserIds(List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> expenseService.createFriendExpense(1L, 2L, request))
                .isInstanceOf(InvalidExpenseParticipantException.class);
    }

    @Test
    void listFriendExpenses_returnsExpensesBetweenThePair() {
        User user = buildUser(1L);
        User friend = buildUser(2L);
        Expense expense = Expense.createFriendExpense(
                user, friend, user, "Coffee", new BigDecimal("20.00"), "INR",
                new BigDecimal("20.00"), BigDecimal.ONE, null, null, SplitType.EQUAL
        );
        ReflectionTestUtils.setField(expense, "id", 200L);

        when(expenseRepository
                .findByGroupIsNullAndFriendUserLowIdAndFriendUserHighIdAndDeletedAtIsNullOrderByCreatedAtDesc(1L, 2L))
                .thenReturn(List.of(expense));
        when(expensePayerRepository.findByExpense_Id(200L)).thenReturn(List.of());
        when(expenseSplitRepository.findByExpense_Id(200L)).thenReturn(List.of());

        List<ExpenseResponse> result = expenseService.listFriendExpenses(1L, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupId()).isNull();
        verify(friendshipService).requireFriends(1L, 2L);
    }
}
