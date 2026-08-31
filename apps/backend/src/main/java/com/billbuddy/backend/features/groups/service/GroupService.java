package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.common.CurrencyUtil;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.UpdateGroupRequest;
import com.billbuddy.backend.features.groups.dto.response.GroupResponse;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupInviteRepository;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
    }

    @Transactional
    public GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Group group = Group.create(
                request.getName(),
                request.getDescription(),
                CurrencyUtil.normalize(request.getDefaultCurrency()),
                creator
        );
        group = groupRepository.save(group);

        GroupMember adminMembership = GroupMember.createAdmin(group, creator);
        groupMemberRepository.save(adminMembership);

        return toResponse(group, creator, 1, GroupRole.ADMIN);
    }

    @Transactional
    public List<GroupResponse> listGroups(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUser_IdAndLeftAtIsNull(userId);

        return memberships.stream()
                .filter(membership -> !membership.getGroup().isDeleted())
                .map(membership -> toResponse(
                        membership.getGroup(),
                        membership.getGroup().getCreatedBy(),
                        groupMemberRepository.countByGroup_IdAndLeftAtIsNull(membership.getGroup().getId()),
                        membership.getRole()
                ))
                .toList();
    }

    @Transactional
    public GroupResponse getGroup(Long groupId, Long userId) {
        GroupMember membership = groupAccessService.requireActiveMember(groupId, userId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        long memberCount = groupMemberRepository.countByGroup_IdAndLeftAtIsNull(groupId);
        return toResponse(group, group.getCreatedBy(), memberCount, membership.getRole());
    }

    @Transactional
    public GroupResponse updateGroup(Long groupId, Long userId, UpdateGroupRequest request) {
        groupAccessService.requireAdmin(groupId, userId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        group.update(request.getName(), request.getDescription(), CurrencyUtil.normalize(request.getDefaultCurrency()));

        long memberCount = groupMemberRepository.countByGroup_IdAndLeftAtIsNull(groupId);
        return toResponse(group, group.getCreatedBy(), memberCount, GroupRole.ADMIN);
    }

    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        groupAccessService.requireAdmin(groupId, userId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        // TODO: block delete unless all balances are settled once Settlements exist
        group.softDelete();

        // prevent joining a "ghost" group via a stale invite
        groupInviteRepository.revokeAllActiveInvites(groupId);
    }

    private GroupResponse toResponse(Group group, User createdBy, long memberCount, GroupRole currentUserRole) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getDefaultCurrency(),
                createdBy.getId(),
                createdBy.getFullName(),
                memberCount,
                currentUserRole,
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
