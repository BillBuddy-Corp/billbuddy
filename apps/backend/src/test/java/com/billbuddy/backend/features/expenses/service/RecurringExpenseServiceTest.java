package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidCurrencyException;
import com.billbuddy.backend.exception.InvalidRecurrenceException;
import com.billbuddy.backend.exception.InvalidSplitException;
import com.billbuddy.backend.exception.NotRecurringExpenseOwnerException;
import com.billbuddy.backend.exception.RecurringExpenseNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.CreateRecurringExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.response.RecurringExpenseResponse;
import com.billbuddy.backend.features.expenses.model.RecurringExpenseTemplate;
import com.billbuddy.backend.features.expenses.model.RecurringFrequency;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.expenses.repository.RecurringExpenseTemplateRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    @Mock
    private RecurringExpenseTemplateRepository templateRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @Mock
    private ExpenseService expenseService;

    // A real ObjectMapper, not mocked -- the split-config JSON round-trip is exactly what these
    // tests need to exercise for real, same reasoning ClaudeReceiptScannerService relies on a real
    // ObjectMapper bean rather than a mock.
    private RecurringExpenseService recurringExpenseService;

    @BeforeEach
    void setUp() {
        recurringExpenseService = new RecurringExpenseService(
                templateRepository, groupRepository, userRepository, groupAccessService, expenseService, new ObjectMapper()
        );
    }

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

    private CreateRecurringExpenseRequest validEqualRequest(String currency) {
        CreateRecurringExpenseRequest request = new CreateRecurringExpenseRequest();
        request.setDescription("Rent");
        request.setAmount(new BigDecimal("90"));
        request.setCurrency(currency);
        request.setSplitType(SplitType.EQUAL);
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        request.setPayers(List.of(payer));
        request.setParticipantUserIds(List.of(1L));
        request.setFrequency(RecurringFrequency.MONTHLY);
        request.setDayOfMonth(1);
        return request;
    }

    // ===================== CREATE =====================

    @Test
    void createTemplate_succeeds_whenValid() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(templateRepository.save(any(RecurringExpenseTemplate.class))).thenAnswer(inv -> {
            RecurringExpenseTemplate t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 100L);
            return t;
        });

        RecurringExpenseResponse response = recurringExpenseService.createTemplate(10L, 1L, validEqualRequest("INR"));

        assertThat(response.getDescription()).isEqualTo("Rent");
        assertThat(response.getFrequency()).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(response.getPayers()).hasSize(1);
        assertThat(response.getParticipantUserIds()).containsExactly(1L);
        assertThat(response.isActive()).isTrue();
        assertThat(response.getNextRunAt()).isAfter(LocalDate.now());
    }

    @Test
    void createTemplate_throwsGroupNotFound_whenGroupMissing() {
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createMember(null, buildUser(1L)));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringExpenseService.createTemplate(10L, 1L, validEqualRequest("INR")))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void createTemplate_throwsInvalidCurrency_whenDifferentCurrencyAndNoRate() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> recurringExpenseService.createTemplate(10L, 1L, validEqualRequest("USD")))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void createTemplate_throwsInvalidRecurrence_whenMonthlyMissingDayOfMonth() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateRecurringExpenseRequest request = validEqualRequest("INR");
        request.setDayOfMonth(null);

        assertThatThrownBy(() -> recurringExpenseService.createTemplate(10L, 1L, request))
                .isInstanceOf(InvalidRecurrenceException.class);
    }

    @Test
    void createTemplate_throwsInvalidRecurrence_whenWeeklyMissingDayOfWeek() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateRecurringExpenseRequest request = validEqualRequest("INR");
        request.setFrequency(RecurringFrequency.WEEKLY);
        request.setDayOfMonth(null);
        request.setDayOfWeek(null);

        assertThatThrownBy(() -> recurringExpenseService.createTemplate(10L, 1L, request))
                .isInstanceOf(InvalidRecurrenceException.class);
    }

    @Test
    void createTemplate_throwsInvalidSplit_whenPayerSumMismatch() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateRecurringExpenseRequest request = validEqualRequest("INR");
        request.getPayers().get(0).setAmountPaid(new BigDecimal("50")); // doesn't match total of 90

        assertThatThrownBy(() -> recurringExpenseService.createTemplate(10L, 1L, request))
                .isInstanceOf(InvalidSplitException.class);

        verify(templateRepository, never()).save(any());
    }

    // ===================== LIST =====================

    @Test
    void listTemplates_returnsMappedTemplates() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(admin, group);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(templateRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of(template));

        List<RecurringExpenseResponse> result = recurringExpenseService.listTemplates(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
        assertThat(result.get(0).getParticipantUserIds()).containsExactly(1L);
    }

    // ===================== PAUSE / RESUME / CANCEL =====================

    @Test
    void pauseTemplate_pauses_whenCreator() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(admin, group);

        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));

        recurringExpenseService.pauseTemplate(100L, 1L);

        assertThat(template.isActive()).isFalse();
    }

    @Test
    void resumeTemplate_resumes_whenGroupAdminButNotCreator() {
        User creator = buildUser(1L);
        User admin = buildUser(2L);
        Group group = buildGroup(10L, creator, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(creator, group);
        template.pause();

        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createAdmin(group, admin));

        recurringExpenseService.resumeTemplate(100L, 2L);

        assertThat(template.isActive()).isTrue();
    }

    @Test
    void cancelTemplate_throwsNotOwner_whenNeitherCreatorNorAdmin() {
        User creator = buildUser(1L);
        User other = buildUser(2L);
        Group group = buildGroup(10L, creator, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(creator, group);

        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, other));

        assertThatThrownBy(() -> recurringExpenseService.cancelTemplate(100L, 2L))
                .isInstanceOf(NotRecurringExpenseOwnerException.class);

        assertThat(template.isCancelled()).isFalse();
    }

    @Test
    void pauseTemplate_throwsNotFound_whenMissing() {
        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringExpenseService.pauseTemplate(100L, 1L))
                .isInstanceOf(RecurringExpenseNotFoundException.class);
    }

    // ===================== computeNextRun =====================

    @Test
    void computeNextRun_weekly_returnsNextMatchingDayOfWeek() {
        // 2026-09-07 is a Monday (ISO dayOfWeek=1); target Wednesday (3)
        LocalDate from = LocalDate.of(2026, 9, 7);
        LocalDate next = recurringExpenseService.computeNextRun(RecurringFrequency.WEEKLY, 3, null, from);
        assertThat(next).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void computeNextRun_weekly_wrapsToNextWeek_whenTargetDayAlreadyPassedThisWeek() {
        // 2026-09-07 is a Monday; target Monday (1) should roll to the following Monday
        LocalDate from = LocalDate.of(2026, 9, 7);
        LocalDate next = recurringExpenseService.computeNextRun(RecurringFrequency.WEEKLY, 1, null, from);
        assertThat(next).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void computeNextRun_monthly_returnsSameDayNextMonth() {
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate next = recurringExpenseService.computeNextRun(RecurringFrequency.MONTHLY, null, 15, from);
        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 15));
    }

    @Test
    void computeNextRun_monthly_clampsToLastDay_whenDayOfMonthExceedsShorterMonth() {
        // from January, day 31 -> February has 28 days in 2026 (not a leap year)
        LocalDate from = LocalDate.of(2026, 1, 5);
        LocalDate next = recurringExpenseService.computeNextRun(RecurringFrequency.MONTHLY, null, 31, from);
        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // ===================== GENERATION =====================

    @Test
    void processDueTemplate_generatesExpenseAndAdvancesNextRun_whenSucceeds() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(admin, group);
        LocalDate originalNextRun = template.getNextRunAt();

        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template));

        recurringExpenseService.processDueTemplate(100L);

        verify(expenseService).createExpense(eq(10L), eq(1L), any(CreateExpenseRequest.class));
        assertThat(template.getNextRunAt()).isAfter(originalNextRun);
    }

    @Test
    void processDueTemplate_stillAdvancesNextRun_whenExpenseCreationFails() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        RecurringExpenseTemplate template = buildPersistedTemplate(admin, group);
        LocalDate originalNextRun = template.getNextRunAt();

        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template));
        when(expenseService.createExpense(any(), any(), any())).thenThrow(new RuntimeException("participant left the group"));

        recurringExpenseService.processDueTemplate(100L);

        assertThat(template.getNextRunAt()).isAfter(originalNextRun);
    }

    @Test
    void processDueTemplate_noOps_whenTemplateNoLongerExists() {
        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        recurringExpenseService.processDueTemplate(100L);

        verifyNoInteractions(expenseService);
    }

    @Test
    void runDueGenerations_processesAllDueTemplates() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");
        RecurringExpenseTemplate template1 = buildPersistedTemplate(admin, group);
        RecurringExpenseTemplate template2 = buildPersistedTemplate(admin, group);
        ReflectionTestUtils.setField(template2, "id", 200L);

        when(templateRepository.findDueTemplates()).thenReturn(List.of(template1, template2));
        when(templateRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(template1));
        when(templateRepository.findByIdAndDeletedAtIsNull(200L)).thenReturn(Optional.of(template2));

        recurringExpenseService.runDueGenerations();

        verify(expenseService, times(2)).createExpense(eq(10L), eq(1L), any(CreateExpenseRequest.class));
    }

    private RecurringExpenseTemplate buildPersistedTemplate(User creator, Group group) {
        PayerEntry payer = new PayerEntry();
        payer.setUserId(1L);
        payer.setAmountPaid(new BigDecimal("90"));
        String splitConfigJson;
        try {
            splitConfigJson = new ObjectMapper().writeValueAsString(
                    Map.of("payers", List.of(payer), "participantUserIds", List.of(1L))
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        RecurringExpenseTemplate template = RecurringExpenseTemplate.create(
                group, creator, "Rent", new BigDecimal("90"), group.getDefaultCurrency(), null, null,
                SplitType.EQUAL, RecurringFrequency.MONTHLY, null, 1, splitConfigJson, LocalDate.now().plusDays(1)
        );
        ReflectionTestUtils.setField(template, "id", 100L);
        return template;
    }
}
