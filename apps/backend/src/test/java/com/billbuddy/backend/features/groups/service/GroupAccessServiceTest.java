package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.exception.NotGroupAdminException;
import com.billbuddy.backend.exception.NotGroupMemberException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupAccessServiceTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private GroupAccessService groupAccessService;

    private User buildUser() {
        return User.signupWithEmail("jane@example.com", "hashed-password", "Jane Doe", null);
    }

    private Group buildGroup(User creator) {
        return Group.create("Goa Trip", "Beach house squad", "INR", creator);
    }

    @Test
    void requireActiveMember_returnsMember_whenActiveMembershipExists() {
        User user = buildUser();
        Group group = buildGroup(user);
        GroupMember member = GroupMember.createMember(group, user);

        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.of(member));

        GroupMember result = groupAccessService.requireActiveMember(1L, 2L);

        assertThat(result).isSameAs(member);
    }

    @Test
    void requireActiveMember_throwsNotGroupMember_whenNoActiveMembership() {
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupAccessService.requireActiveMember(1L, 2L))
                .isInstanceOf(NotGroupMemberException.class);
    }

    @Test
    void requireAdmin_returnsMember_whenActiveAdmin() {
        User user = buildUser();
        Group group = buildGroup(user);
        GroupMember admin = GroupMember.createAdmin(group, user);

        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.of(admin));

        GroupMember result = groupAccessService.requireAdmin(1L, 2L);

        assertThat(result).isSameAs(admin);
    }

    @Test
    void requireAdmin_throwsNotGroupAdmin_whenActiveButOnlyMember() {
        User user = buildUser();
        Group group = buildGroup(user);
        GroupMember member = GroupMember.createMember(group, user);

        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> groupAccessService.requireAdmin(1L, 2L))
                .isInstanceOf(NotGroupAdminException.class);
    }

    @Test
    void requireAdmin_throwsNotGroupMember_whenNotAMemberAtAll() {
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupAccessService.requireAdmin(1L, 2L))
                .isInstanceOf(NotGroupMemberException.class);
    }
}
