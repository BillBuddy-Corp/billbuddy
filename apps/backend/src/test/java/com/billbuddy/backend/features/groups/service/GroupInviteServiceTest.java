package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.common.EmailService;
import com.billbuddy.backend.exception.AlreadyGroupMemberException;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidInviteException;
import com.billbuddy.backend.exception.InviteNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import com.billbuddy.backend.features.groups.dto.response.GroupInviteResponse;
import com.billbuddy.backend.features.groups.dto.response.JoinInviteResponse;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupInvite;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupInviteRepository;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupInviteServiceTest {

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

    @Mock
    private EmailService emailService;

    @InjectMocks
    private GroupInviteService groupInviteService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(groupInviteService, "emailExpiryDays", 7);
        ReflectionTestUtils.setField(groupInviteService, "joinLinkBaseUrl", "http://localhost:3000/invites/join");
    }

    private User buildUser(Long id, String email) {
        User user = User.signupWithEmail(email, "hashed-password", "User " + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Group buildGroup(Long id, User creator) {
        Group group = Group.create("Goa Trip", "Beach house squad", "INR", creator);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    // ===================== CREATE EMAIL INVITE =====================

    @Test
    void createEmailInvite_sendsEmailAndPersistsHashedToken_whenActiveMember() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        groupInviteService.createEmailInvite(10L, 1L, "Friend@Example.com");

        verify(groupInviteRepository).revokeActivePendingEmailInvites(10L, "friend@example.com");

        ArgumentCaptor<GroupInvite> inviteCaptor = ArgumentCaptor.forClass(GroupInvite.class);
        verify(groupInviteRepository).save(inviteCaptor.capture());
        GroupInvite savedInvite = inviteCaptor.getValue();
        assertThat(savedInvite.getEmail()).isEqualTo("friend@example.com");
        assertThat(savedInvite.getToken()).hasSize(64); // sha256 hex
        assertThat(savedInvite.getExpiresAt()).isAfter(LocalDateTime.now());

        verify(emailService).sendInviteEmail(eq("friend@example.com"), eq("Goa Trip"), anyString());
    }

    @Test
    void createEmailInvite_throwsAlreadyGroupMember_whenEmailBelongsToActiveMember() {
        User admin = buildUser(1L, "admin@example.com");
        User existingMember = buildUser(2L, "friend@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(existingMember));
        when(groupMemberRepository.findByGroup_IdAndUser_IdAndLeftAtIsNull(10L, 2L))
                .thenReturn(Optional.of(GroupMember.createMember(group, existingMember)));

        assertThatThrownBy(() -> groupInviteService.createEmailInvite(10L, 1L, "friend@example.com"))
                .isInstanceOf(AlreadyGroupMemberException.class);

        verify(groupInviteRepository, never()).save(any());
        verify(emailService, never()).sendInviteEmail(anyString(), anyString(), anyString());
    }

    // ===================== LIST INVITES =====================

    @Test
    void listInvites_exposesTokenOnlyForLinkType() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        GroupInvite emailInvite = GroupInvite.createEmailInvite(
                group, "friend@example.com", "hashed-token", admin, LocalDateTime.now().plusDays(7)
        );
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);

        when(groupAccessService.requireActiveMember(10L, 1L)).thenReturn(adminMembership);
        when(groupInviteRepository.findByGroup_IdAndRevokedFalse(10L))
                .thenReturn(List.of(emailInvite, linkInvite));

        List<GroupInviteResponse> responses = groupInviteService.listInvites(10L, 1L);

        assertThat(responses).hasSize(2);
        GroupInviteResponse emailResponse = responses.stream()
                .filter(r -> r.getEmail() != null).findFirst().orElseThrow();
        GroupInviteResponse linkResponse = responses.stream()
                .filter(r -> r.getEmail() == null).findFirst().orElseThrow();

        assertThat(emailResponse.getToken()).isNull();
        assertThat(linkResponse.getToken()).isEqualTo("raw-link-token");
    }

    // ===================== REVOKE INVITE =====================

    @Test
    void revokeInvite_revokesInvite_whenFoundInGroup() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);
        GroupInvite invite = GroupInvite.createLinkInvite(group, "raw-token", admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupInviteRepository.findByIdAndGroup_Id(5L, 10L)).thenReturn(Optional.of(invite));

        groupInviteService.revokeInvite(10L, 1L, 5L);

        assertThat(invite.isRevoked()).isTrue();
    }

    @Test
    void revokeInvite_throwsInviteNotFound_whenInviteBelongsToDifferentGroup() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupInviteRepository.findByIdAndGroup_Id(5L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupInviteService.revokeInvite(10L, 1L, 5L))
                .isInstanceOf(InviteNotFoundException.class);
    }

    // ===================== LINK GENERATE / DISABLE =====================

    @Test
    void generateLink_revokesExistingActiveLinkBeforeCreatingNew() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);
        when(groupRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(groupInviteRepository.save(any(GroupInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupInviteResponse response = groupInviteService.generateLink(10L, 1L);

        verify(groupInviteRepository).revokeActiveLinkInvites(10L);
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getExpiresAt()).isNull();
    }

    @Test
    void disableLink_revokesActiveLinkInvites_whenAdmin() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupMember adminMembership = GroupMember.createAdmin(group, admin);

        when(groupAccessService.requireAdmin(10L, 1L)).thenReturn(adminMembership);

        groupInviteService.disableLink(10L, 1L);

        verify(groupInviteRepository).revokeActiveLinkInvites(10L);
    }

    // ===================== JOIN VIA INVITE =====================

    @Test
    void joinViaInvite_joinsNewMember_viaLinkToken() {
        User admin = buildUser(1L, "admin@example.com");
        User joiner = buildUser(2L, "joiner@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);

        when(groupInviteRepository.findByToken("raw-link-token")).thenReturn(Optional.of(linkInvite));
        when(userRepository.findById(2L)).thenReturn(Optional.of(joiner));
        when(groupMemberRepository.findByGroup_IdAndUser_Id(10L, 2L)).thenReturn(Optional.empty());

        JoinInviteResponse response = groupInviteService.joinViaInvite(2L, "raw-link-token");

        assertThat(response.getGroupId()).isEqualTo(10L);
        assertThat(response.getRole().name()).isEqualTo("MEMBER");
        verify(groupMemberRepository).save(any(GroupMember.class));
        assertThat(linkInvite.getAcceptedAt()).isNull(); // LINK invites are never marked accepted
    }

    @Test
    void joinViaInvite_joinsViaEmailToken_marksInviteAccepted() {
        User admin = buildUser(1L, "admin@example.com");
        User joiner = buildUser(2L, "joiner@example.com");
        Group group = buildGroup(10L, admin);
        String rawToken = "raw-email-token";
        String hash = TokenHashUtil.sha256(rawToken);
        GroupInvite emailInvite = GroupInvite.createEmailInvite(
                group, "joiner@example.com", hash, admin, LocalDateTime.now().plusDays(7)
        );

        when(groupInviteRepository.findByToken(rawToken)).thenReturn(Optional.empty());
        when(groupInviteRepository.findByToken(hash)).thenReturn(Optional.of(emailInvite));
        when(userRepository.findById(2L)).thenReturn(Optional.of(joiner));
        when(groupMemberRepository.findByGroup_IdAndUser_Id(10L, 2L)).thenReturn(Optional.empty());

        groupInviteService.joinViaInvite(2L, rawToken);

        assertThat(emailInvite.getAcceptedAt()).isNotNull();
    }

    @Test
    void joinViaInvite_rejoin_reusesExistingRow_whenPreviouslyLeft() {
        User admin = buildUser(1L, "admin@example.com");
        User rejoiningUser = buildUser(2L, "rejoin@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);

        GroupMember previousMembership = GroupMember.createMember(group, rejoiningUser);
        previousMembership.leave();

        when(groupInviteRepository.findByToken("raw-link-token")).thenReturn(Optional.of(linkInvite));
        when(userRepository.findById(2L)).thenReturn(Optional.of(rejoiningUser));
        when(groupMemberRepository.findByGroup_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(previousMembership));

        groupInviteService.joinViaInvite(2L, "raw-link-token");

        assertThat(previousMembership.isActive()).isTrue();
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void joinViaInvite_throwsInviteNotFound_whenTokenMatchesNothing() {
        when(groupInviteRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "bogus-token"))
                .isInstanceOf(InviteNotFoundException.class);
    }

    @Test
    void joinViaInvite_throwsInvalidInvite_whenRevoked() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);
        linkInvite.revoke();

        when(groupInviteRepository.findByToken("raw-link-token")).thenReturn(Optional.of(linkInvite));

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "raw-link-token"))
                .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void joinViaInvite_throwsInvalidInvite_whenExpired() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite emailInvite = GroupInvite.createEmailInvite(
                group, "friend@example.com", "hashed-token", admin, LocalDateTime.now().minusDays(1)
        );

        when(groupInviteRepository.findByToken("raw-email-token")).thenReturn(Optional.empty());
        when(groupInviteRepository.findByToken(TokenHashUtil.sha256("raw-email-token")))
                .thenReturn(Optional.of(emailInvite));

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "raw-email-token"))
                .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void joinViaInvite_throwsInvalidInvite_whenEmailInviteAlreadyAccepted() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite emailInvite = GroupInvite.createEmailInvite(
                group, "friend@example.com", "hashed-token", admin, LocalDateTime.now().plusDays(7)
        );
        emailInvite.accept();

        when(groupInviteRepository.findByToken("raw-email-token")).thenReturn(Optional.empty());
        when(groupInviteRepository.findByToken(TokenHashUtil.sha256("raw-email-token")))
                .thenReturn(Optional.of(emailInvite));

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "raw-email-token"))
                .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void joinViaInvite_throwsAlreadyGroupMember_whenAlreadyActive() {
        User admin = buildUser(1L, "admin@example.com");
        User joiner = buildUser(2L, "joiner@example.com");
        Group group = buildGroup(10L, admin);
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);
        GroupMember activeMembership = GroupMember.createMember(group, joiner);

        when(groupInviteRepository.findByToken("raw-link-token")).thenReturn(Optional.of(linkInvite));
        when(userRepository.findById(2L)).thenReturn(Optional.of(joiner));
        when(groupMemberRepository.findByGroup_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(activeMembership));

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "raw-link-token"))
                .isInstanceOf(AlreadyGroupMemberException.class);
    }

    @Test
    void joinViaInvite_throwsGroupNotFound_whenGroupSoftDeleted() {
        User admin = buildUser(1L, "admin@example.com");
        Group group = buildGroup(10L, admin);
        group.softDelete();
        GroupInvite linkInvite = GroupInvite.createLinkInvite(group, "raw-link-token", admin);

        when(groupInviteRepository.findByToken("raw-link-token")).thenReturn(Optional.of(linkInvite));

        assertThatThrownBy(() -> groupInviteService.joinViaInvite(2L, "raw-link-token"))
                .isInstanceOf(GroupNotFoundException.class);
    }
}
