package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.LastAdminException;
import com.billbuddy.backend.exception.MemberNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.dto.response.GroupMemberResponse;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAccessService groupAccessService;

    public GroupMemberService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupAccessService groupAccessService
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupAccessService = groupAccessService;
    }

    @Transactional
    public List<GroupMemberResponse> listMembers(Long groupId, Long userId) {
        groupAccessService.requireActiveMember(groupId, userId);
        requireGroupExists(groupId);

        return groupMemberRepository.findByGroup_IdAndLeftAtIsNull(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeMember(Long groupId, Long requesterId, Long targetUserId) {
        groupAccessService.requireActiveMember(groupId, requesterId);
        requireGroupExists(groupId);

        GroupMember target = groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(groupId, targetUserId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found"));

        boolean isSelfRemoval = requesterId.equals(targetUserId);

        if (isSelfRemoval) {
            guardAgainstStrandingGroup(groupId, target);
        } else {
            groupAccessService.requireAdmin(groupId, requesterId);
        }

        target.leave();
    }

    @Transactional
    public GroupMemberResponse changeRole(Long groupId, Long requesterId, Long targetUserId, GroupRole newRole) {
        groupAccessService.requireAdmin(groupId, requesterId);
        requireGroupExists(groupId);

        GroupMember target = groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(groupId, targetUserId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found"));

        boolean isDemotion = target.getRole() == GroupRole.ADMIN && newRole == GroupRole.MEMBER;
        if (isDemotion) {
            guardAgainstStrandingGroup(groupId, target);
        }

        target.changeRole(newRole);
        return toResponse(target);
    }

    private void requireGroupExists(Long groupId) {
        groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
    }

    private void guardAgainstStrandingGroup(Long groupId, GroupMember target) {
        if (target.getRole() != GroupRole.ADMIN) {
            return;
        }
        long activeAdmins = groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(groupId, GroupRole.ADMIN);
        long activeMembers = groupMemberRepository.countByGroup_IdAndLeftAtIsNull(groupId);
        boolean otherMembersRemain = activeMembers > 1;

        if (activeAdmins <= 1 && otherMembersRemain) {
            throw new LastAdminException("Promote another admin before leaving or stepping down");
        }
    }

    private GroupMemberResponse toResponse(GroupMember member) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
