package com.billbuddy.backend.features.settlements.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidSettlementException;
import com.billbuddy.backend.exception.InvalidSettlementParticipantException;
import com.billbuddy.backend.exception.NotSettlementOwnerException;
import com.billbuddy.backend.exception.SettlementNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.settlements.dto.request.CreateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.request.UpdateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.response.SettlementResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @InjectMocks
    private SettlementService settlementService;

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

    private Settlement buildSettlement(Long id, Group group, User paidBy, User paidTo, User createdBy, BigDecimal amount) {
        Settlement settlement = Settlement.create(group, paidBy, paidTo, createdBy, amount, group.getDefaultCurrency(), null);
        ReflectionTestUtils.setField(settlement, "id", id);
        return settlement;
    }

    private void stubActiveMembers(Long groupId, User... users) {
        List<GroupMember> members = List.of(users).stream()
                .map(u -> GroupMember.createMember(null, u))
                .toList();
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(groupId)).thenReturn(members);
    }

    private CreateSettlementRequest buildCreateRequest(Long paidByUserId, Long paidToUserId, String amount, String currency) {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setPaidByUserId(paidByUserId);
        request.setPaidToUserId(paidToUserId);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency(currency);
        return request;
    }

    // ===================== CREATE =====================

    @Test
    void createSettlement_savesSettlement_whenValid() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member1));
        stubActiveMembers(10L, admin, member1);
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        CreateSettlementRequest request = buildCreateRequest(2L, 1L, "50", "INR");

        SettlementResponse response = settlementService.createSettlement(10L, 1L, request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getPaidByUserId()).isEqualTo(2L);
        assertThat(response.getPaidToUserId()).isEqualTo(1L);
        assertThat(response.getAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getCreatedByUserId()).isEqualTo(1L);
    }

    @Test
    void createSettlement_throwsGroupNotFound_whenGroupMissing() {
        GroupMember membership = GroupMember.createMember(null, buildUser(1L));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        CreateSettlementRequest request = buildCreateRequest(1L, 2L, "50", "INR");

        assertThatThrownBy(() -> settlementService.createSettlement(10L, 1L, request))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void createSettlement_throwsInvalidSettlement_whenPaidByEqualsPaidTo() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        CreateSettlementRequest request = buildCreateRequest(1L, 1L, "50", "INR");

        assertThatThrownBy(() -> settlementService.createSettlement(10L, 1L, request))
                .isInstanceOf(InvalidSettlementException.class);

        verify(settlementRepository, never()).save(any());
    }

    @Test
    void createSettlement_throwsInvalidSettlementParticipant_whenPaidToNotActiveMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        // only admin is an active member -- userId 99 is not
        stubActiveMembers(10L, admin);

        CreateSettlementRequest request = buildCreateRequest(1L, 99L, "50", "INR");

        assertThatThrownBy(() -> settlementService.createSettlement(10L, 1L, request))
                .isInstanceOf(InvalidSettlementParticipantException.class);
    }

    @Test
    void createSettlement_throwsInvalidCurrency_whenCurrencyMismatchesGroup() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        stubActiveMembers(10L, admin, member1);

        CreateSettlementRequest request = buildCreateRequest(2L, 1L, "50", "USD");

        assertThatThrownBy(() -> settlementService.createSettlement(10L, 1L, request))
                .isInstanceOf(com.billbuddy.backend.exception.InvalidCurrencyException.class);

        verify(settlementRepository, never()).save(any());
    }

    // ===================== GET / LIST =====================

    @Test
    void getSettlement_returnsResponse_whenActiveMember() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, admin, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));

        SettlementResponse response = settlementService.getSettlement(100L, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getGroupId()).isEqualTo(10L);
    }

    @Test
    void getSettlement_throwsSettlementNotFound_whenMissing() {
        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getSettlement(100L, 1L))
                .isInstanceOf(SettlementNotFoundException.class);
    }

    @Test
    void listSettlements_returnsMappedSettlements() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, admin, new BigDecimal("50.00"));

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(settlementRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of(settlement));

        List<SettlementResponse> result = settlementService.listSettlements(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    // ===================== UPDATE =====================

    @Test
    void updateSettlement_succeeds_whenCreator() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, member1, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, member1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        stubActiveMembers(10L, admin, member1);

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("25"));
        request.setCurrency("INR");

        SettlementResponse response = settlementService.updateSettlement(100L, 2L, request);

        assertThat(response.getAmount()).isEqualByComparingTo("25.00");
        assertThat(settlement.getAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void updateSettlement_succeeds_whenGroupAdminButNotLogger() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, member1, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(GroupMember.createAdmin(group, admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        stubActiveMembers(10L, admin, member1);

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("40"));
        request.setCurrency("INR");

        SettlementResponse response = settlementService.updateSettlement(100L, 1L, request);

        assertThat(response.getAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void updateSettlement_throwsNotSettlementOwner_whenNeitherLoggerNorAdmin() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, member1, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 3L)).thenReturn(GroupMember.createMember(group, member2));

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setPaidByUserId(2L);
        request.setPaidToUserId(1L);
        request.setAmount(new BigDecimal("50"));
        request.setCurrency("INR");

        assertThatThrownBy(() -> settlementService.updateSettlement(100L, 3L, request))
                .isInstanceOf(NotSettlementOwnerException.class);

        assertThat(settlement.getAmount()).isEqualByComparingTo("50.00");
    }

    // ===================== DELETE =====================

    @Test
    void deleteSettlement_softDeletes_whenLogger() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, member1, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(GroupMember.createMember(group, member1));

        settlementService.deleteSettlement(100L, 2L);

        assertThat(settlement.isDeleted()).isTrue();
    }

    @Test
    void deleteSettlement_throwsNotSettlementOwner_whenNeitherLoggerNorAdmin() {
        User admin = buildUser(1L);
        User member1 = buildUser(2L);
        User member2 = buildUser(3L);
        Group group = buildGroup(10L, admin, "INR");
        Settlement settlement = buildSettlement(100L, group, member1, admin, member1, new BigDecimal("50.00"));

        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(settlement));
        when(groupAccessService.requireActiveMember(10L, 3L)).thenReturn(GroupMember.createMember(group, member2));

        assertThatThrownBy(() -> settlementService.deleteSettlement(100L, 3L))
                .isInstanceOf(NotSettlementOwnerException.class);

        assertThat(settlement.isDeleted()).isFalse();
    }

    @Test
    void deleteSettlement_throwsSettlementNotFound_whenMissing() {
        when(settlementRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.deleteSettlement(100L, 1L))
                .isInstanceOf(SettlementNotFoundException.class);
    }
}
