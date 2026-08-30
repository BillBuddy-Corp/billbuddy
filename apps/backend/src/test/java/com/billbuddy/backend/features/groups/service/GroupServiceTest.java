package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidCurrencyException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupInviteRepository groupInviteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccessService groupAccessService;

    @InjectMocks
    private GroupService groupService;

    private User buildUser(Long id) {
        User user = User.signupWithEmail("jane@example.com", "hashed-password", "Jane Doe", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator) {
        Group group = Group.create("Goa Trip", "Beach house squad", "INR", creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    // ===================== CREATE =====================

    @Test
    void createGroup_savesGroupAndMakesCreatorAdmin() {
        User creator = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 10L);
            return group;
        });

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDescription("Beach house squad");
        request.setDefaultCurrency("INR");

        GroupResponse response = groupService.createGroup(1L, request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCurrentUserRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(response.getMemberCount()).isEqualTo(1);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(GroupRole.ADMIN);
    }

    @Test
    void createGroup_throwsUserNotFound_whenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("INR");

        assertThatThrownBy(() -> groupService.createGroup(1L, request))
                .isInstanceOf(UserNotFoundException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_normalizesCurrencyToUppercase() {
        User creator = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            ReflectionTestUtils.setField(group, "id", 10L);
            return group;
        });

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("inr");

        GroupResponse response = groupService.createGroup(1L, request);

        assertThat(response.getDefaultCurrency()).isEqualTo("INR");
    }

    @Test
    void createGroup_throwsInvalidCurrency_whenCodeIsNotARealCurrency() {
        User creator = buildUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("ZZZ");

        assertThatThrownBy(() -> groupService.createGroup(1L, request))
                .isInstanceOf(InvalidCurrencyException.class);

        verify(groupRepository, never()).save(any());
    }

    // ===================== LIST =====================

    @Test
    void listGroups_excludesDeletedGroups() {
        User user = buildUser(1L);
        Group activeGroup = buildGroup(10L, user);
        Group deletedGroup = buildGroup(11L, user);
        deletedGroup.softDelete();

        GroupMember activeMembership = GroupMember.createAdmin(activeGroup, user);
        GroupMember deletedGroupMembership = GroupMember.createAdmin(deletedGroup, user);

        when(groupMemberRepository.findByUser_IdAndLeftAtIsNull(1L))
                .thenReturn(List.of(activeMembership, deletedGroupMembership));
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(1L);

        List<GroupResponse> result = groupService.listGroups(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    // ===================== GET =====================

    @Test
    void getGroup_returnsResponse_whenActiveMember() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        GroupMember membership = GroupMember.createMember(group, user);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(3L);

        GroupResponse response = groupService.getGroup(10L, 1L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCurrentUserRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(response.getMemberCount()).isEqualTo(3);
    }

    @Test
    void getGroup_throwsGroupNotFound_whenGroupSoftDeleted() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        GroupMember membership = GroupMember.createMember(group, user);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(membership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroup(10L, 1L))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ===================== UPDATE =====================

    @Test
    void updateGroup_updatesFields_whenAdmin() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        GroupMember admin = GroupMember.createAdmin(group, user);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(admin);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.countByGroup_IdAndLeftAtIsNull(10L)).thenReturn(1L);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Renamed Trip");
        request.setDescription("Updated description");
        request.setDefaultCurrency("USD");

        GroupResponse response = groupService.updateGroup(10L, 1L, request);

        assertThat(response.getName()).isEqualTo("Renamed Trip");
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getDefaultCurrency()).isEqualTo("USD");
        assertThat(group.getName()).isEqualTo("Renamed Trip");
    }

    @Test
    void updateGroup_throwsInvalidCurrency_whenCodeIsNotARealCurrency() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        GroupMember admin = GroupMember.createAdmin(group, user);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(admin);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("not-a-currency");

        assertThatThrownBy(() -> groupService.updateGroup(10L, 1L, request))
                .isInstanceOf(InvalidCurrencyException.class);

        assertThat(group.getDefaultCurrency()).isEqualTo("INR"); // unchanged
    }

    // ===================== DELETE =====================

    @Test
    void deleteGroup_softDeletesAndRevokesInvites_whenAdmin() {
        User user = buildUser(1L);
        Group group = buildGroup(10L, user);
        GroupMember admin = GroupMember.createAdmin(group, user);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(admin);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));

        groupService.deleteGroup(10L, 1L);

        assertThat(group.isDeleted()).isTrue();
        verify(groupInviteRepository).revokeAllActiveInvites(10L);
    }
}
