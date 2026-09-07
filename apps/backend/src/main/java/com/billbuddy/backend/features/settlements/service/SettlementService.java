package com.billbuddy.backend.features.settlements.service;

import com.billbuddy.backend.common.CurrencyUtil;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidSettlementException;
import com.billbuddy.backend.exception.InvalidSettlementParticipantException;
import com.billbuddy.backend.exception.NotSettlementOwnerException;
import com.billbuddy.backend.exception.SettlementNotFoundException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.service.GroupAccessService;
import com.billbuddy.backend.features.notifications.service.NotificationService;
import com.billbuddy.backend.features.settlements.dto.request.CreateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.request.UpdateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.response.SettlementResponse;
import com.billbuddy.backend.features.settlements.model.Settlement;
import com.billbuddy.backend.features.settlements.repository.SettlementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;
    private final NotificationService notificationService;

    public SettlementService(
            SettlementRepository settlementRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService,
            NotificationService notificationService
    ) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SettlementResponse createSettlement(Long groupId, Long requesterId, CreateSettlementRequest request) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        User createdBy = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        validateParties(request.getPaidByUserId(), request.getPaidToUserId(), groupId);
        String currency = CurrencyUtil.normalizeAndRequireMatch(request.getCurrency(), group.getDefaultCurrency(), "Settlement");

        User paidBy = userRepository.findById(request.getPaidByUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User paidTo = userRepository.findById(request.getPaidToUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Settlement settlement = Settlement.create(
                group, paidBy, paidTo, createdBy,
                request.getAmount().setScale(2, RoundingMode.HALF_UP), currency, request.getNote()
        );
        settlement = settlementRepository.save(settlement);
        notificationService.notifySettlementRecorded(settlement, requesterId);

        return toResponse(settlement);
    }

    @Transactional
    public List<SettlementResponse> listSettlements(Long groupId, Long requesterId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        return settlementRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SettlementResponse getSettlement(Long settlementId, Long requesterId) {
        Settlement settlement = settlementRepository.findByIdAndDeletedAtIsNull(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found"));
        groupAccessService.requireActiveMember(settlement.getGroup().getId(), requesterId);
        return toResponse(settlement);
    }

    @Transactional
    public SettlementResponse updateSettlement(Long settlementId, Long requesterId, UpdateSettlementRequest request) {
        Settlement settlement = settlementRepository.findByIdAndDeletedAtIsNull(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found"));
        requireCreatorOrAdmin(settlement, requesterId);

        Group group = settlement.getGroup();
        validateParties(request.getPaidByUserId(), request.getPaidToUserId(), group.getId());
        String currency = CurrencyUtil.normalizeAndRequireMatch(request.getCurrency(), group.getDefaultCurrency(), "Settlement");

        User paidBy = userRepository.findById(request.getPaidByUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User paidTo = userRepository.findById(request.getPaidToUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        settlement.update(paidBy, paidTo, request.getAmount().setScale(2, RoundingMode.HALF_UP), currency, request.getNote());

        return toResponse(settlement);
    }

    @Transactional
    public void deleteSettlement(Long settlementId, Long requesterId) {
        Settlement settlement = settlementRepository.findByIdAndDeletedAtIsNull(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException("Settlement not found"));
        requireCreatorOrAdmin(settlement, requesterId);
        settlement.softDelete();
    }

    // ===================== HELPERS =====================

    private void validateParties(Long paidByUserId, Long paidToUserId, Long groupId) {
        if (paidByUserId.equals(paidToUserId)) {
            throw new InvalidSettlementException("paidByUserId and paidToUserId must be different");
        }

        Set<Long> activeMemberIds = groupMemberRepository.findByGroup_IdAndLeftAtIsNull(groupId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        if (!activeMemberIds.contains(paidByUserId)) {
            throw new InvalidSettlementParticipantException("User " + paidByUserId + " is not an active member of this group");
        }
        if (!activeMemberIds.contains(paidToUserId)) {
            throw new InvalidSettlementParticipantException("User " + paidToUserId + " is not an active member of this group");
        }
    }

    private void requireCreatorOrAdmin(Settlement settlement, Long requesterId) {
        GroupMember requesterMembership = groupAccessService.requireActiveMember(settlement.getGroup().getId(), requesterId);
        boolean isCreator = settlement.getCreatedBy().getId().equals(requesterId);
        boolean isAdmin = requesterMembership.getRole() == GroupRole.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new NotSettlementOwnerException("Only the person who logged this settlement or a group admin can modify it");
        }
    }

    private SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                settlement.getPaidBy().getId(),
                settlement.getPaidBy().getFullName(),
                settlement.getPaidTo().getId(),
                settlement.getPaidTo().getFullName(),
                settlement.getAmount(),
                settlement.getCurrency(),
                settlement.getNote(),
                settlement.getCreatedBy().getId(),
                settlement.getCreatedBy().getFullName(),
                settlement.getCreatedAt(),
                settlement.getUpdatedAt()
        );
    }
}
