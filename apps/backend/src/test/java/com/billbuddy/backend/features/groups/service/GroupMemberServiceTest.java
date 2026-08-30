package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.exception.LastAdminException;
import com.billbuddy.backend.exception.MemberNotFoundException;
import com.billbuddy.backend.exception.NotGroupAdminException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.dto.response.GroupMemberResponse;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @InjectMocks
    private GroupMemberService groupMemberService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("user" + id + "@example.com", "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator) {
        Group group = Group.create("Goa Trip", "Beach house squad", "INR", creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    // ===================== LIST =====================

    @Test
    void listMembers_returnsActiveMembers() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndLeftAtIsNull(10L)).thenReturn(List.of(adminMembership));

        List<GroupMemberResponse> result = groupMemberService.listMembers(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getRole()).isEqualTo(GroupRole.ADMIN);
    }

    // ===================== REMOVE / LEAVE =====================

    @Test
    void removeMember_selfLeave_succeeds_whenAnotherMemberRemainsAsAdmin() {
        User admin = buildUser(1L);
        User leavingMember = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        GroupMember leavingMembership = GroupMember.createMember(group, leavingMember);

        when(groupAccessService.requireActiveMember(10L, 2L)).thenReturn(leavingMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(leavingMembership));

        groupMemberService.removeMember(10L, 2L, 2L);

        assertThat(leavingMembership.isActive()).isFalse();
    }

    @Test
    void removeMember_selfLeave_throwsLastAdmin_whenSoleAdminAndOtherMembersRemain() {
        User admin = buildUser(1L);
        User otherMember = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(10L, GroupRole.ADMIN)).thenReturn(1L);
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(2L);

        assertThatThrownBy(() -> groupMemberService.removeMember(10L, 1L, 1L))
                .isInstanceOf(LastAdminException.class);

        assertThat(adminMembership.isActive()).isTrue();
    }

    @Test
    void removeMember_selfLeave_allowed_whenSoleAdminIsTheOnlyMemberLeft() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(10L, GroupRole.ADMIN)).thenReturn(1L);
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(1L);

        groupMemberService.removeMember(10L, 1L, 1L);

        assertThat(adminMembership.isActive()).isFalse();
    }

    @Test
    void removeMember_adminRemovesOther_neverTripsLastAdminGuard() {
        User admin = buildUser(1L);
        User target = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        GroupMember targetMembership = GroupMember.createAdmin(group, target);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(targetMembership));
        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);

        groupMemberService.removeMember(10L, 1L, 2L);

        assertThat(targetMembership.isActive()).isFalse();
    }

    @Test
    void removeMember_nonAdminRemovingOther_throwsNotGroupAdmin() {
        User member = buildUser(1L);
        User target = buildUser(2L);
        Group group = buildGroup(10L, member);
        GroupMember memberMembership = GroupMember.createMember(group, member);
        GroupMember targetMembership = GroupMember.createMember(group, target);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(memberMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(targetMembership));
        when(groupAccessService.requireAdmin(10L, 1L))
                .thenThrow(new NotGroupAdminException("Only group admins can perform this action"));

        assertThatThrownBy(() -> groupMemberService.removeMember(10L, 1L, 2L))
                .isInstanceOf(NotGroupAdminException.class);

        assertThat(targetMembership.isActive()).isTrue();
    }

    @Test
    void removeMember_throwsMemberNotFound_whenTargetNotActiveMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.removeMember(10L, 1L, 99L))
                .isInstanceOf(MemberNotFoundException.class);
    }

    // ===================== CHANGE ROLE =====================

    @Test
    void changeRole_promotesMember_whenAdmin() {
        User admin = buildUser(1L);
        User target = buildUser(2L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        GroupMember targetMembership = GroupMember.createMember(group, target);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(targetMembership));

        GroupMemberResponse response = groupMemberService.changeRole(10L, 1L, 2L, GroupRole.ADMIN);

        assertThat(response.getRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(targetMembership.getRole()).isEqualTo(GroupRole.ADMIN);
    }

    @Test
    void changeRole_demotion_throwsLastAdmin_whenTargetIsSoleAdminAndOthersRemain() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(adminMembership));
        when(groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(10L, GroupRole.ADMIN)).thenReturn(1L);
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(2L);

        assertThatThrownBy(() -> groupMemberService.changeRole(10L, 1L, 1L, GroupRole.MEMBER))
                .isInstanceOf(LastAdminException.class);

        assertThat(adminMembership.getRole()).isEqualTo(GroupRole.ADMIN);
    }

    @Test
    void changeRole_demotion_succeeds_whenAnotherAdminExists() {
        User admin1 = buildUser(1L);
        User admin2 = buildUser(2L);
        Group group = buildGroup(10L, admin1);
        GroupMember admin1Membership = GroupMember.createAdmin(group, admin1);
        GroupMember admin2Membership = GroupMember.createAdmin(group, admin2);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(admin1Membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(admin2Membership));
        when(groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(10L, GroupRole.ADMIN)).thenReturn(2L);
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(2L);

        groupMemberService.changeRole(10L, 1L, 2L, GroupRole.MEMBER);

        assertThat(admin2Membership.getRole()).isEqualTo(GroupRole.MEMBER);
    }

    @Test
    void changeRole_throwsMemberNotFound_whenTargetNotActiveMember() {
        User admin = buildUser(1L);
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.changeRole(10L, 1L, 99L, GroupRole.ADMIN))
                .isInstanceOf(MemberNotFoundException.class);
    }
}
