package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.exception.NotGroupAdminException;
import com.billbuddy.backend.exception.NotGroupMemberException;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class GroupAccessService {

    private final GroupMemberRepository groupMemberRepository;

    public GroupAccessService(GroupMemberRepository groupMemberRepository) {
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupMember requireActiveMember(Long groupId, Long userId) {
        return groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(groupId, userId)
                .orElseThrow(() -> new NotGroupMemberException("You are not a member of this group"));
    }

    public GroupMember requireAdmin(Long groupId, Long userId) {
        GroupMember member = requireActiveMember(groupId, userId);
        if (member.getRole() != GroupRole.ADMIN) {
            throw new NotGroupAdminException("Only group admins can perform this action");
        }
        return member;
    }
}
