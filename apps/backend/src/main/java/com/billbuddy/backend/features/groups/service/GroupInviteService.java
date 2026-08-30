package com.billbuddy.backend.features.groups.service;

import com.billbuddy.backend.common.EmailService;
import com.billbuddy.backend.exception.AlreadyGroupMemberException;
import com.billbuddy.backend.exception.GroupNotFoundException;
import com.billbuddy.backend.exception.InvalidInviteException;
import com.billbuddy.backend.exception.InviteNotFoundException;
import com.billbuddy.backend.exception.UserNotFoundException;
import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.auth.util.TokenHashUtil;
import com.billbuddy.backend.features.groups.dto.response.GroupInviteResponse;
import com.billbuddy.backend.features.groups.dto.response.JoinInviteResponse;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupInvite;
import com.billbuddy.backend.features.groups.model.GroupInviteType;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.repository.GroupInviteRepository;
import com.billbuddy.backend.features.groups.repository.GroupMemberRepository;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.groups.util.InviteTokenUtil;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GroupInviteService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final UserRepository userRepository;
    private final GroupAccessService groupAccessService;
    private final EmailService emailService;

    @Value("${invite.email-expiry-days}")
    private int emailExpiryDays;

    @Value("${app.join-link-base-url}")
    private String joinLinkBaseUrl;

    public GroupInviteService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository,
            UserRepository userRepository,
            GroupAccessService groupAccessService,
            EmailService emailService
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.userRepository = userRepository;
        this.groupAccessService = groupAccessService;
        this.emailService = emailService;
    }

    @Transactional
    public void createEmailInvite(Long groupId, Long requesterId, String rawEmail) {
        groupAccessService.requireAdmin(groupId, requesterId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        String email = normalizeEmail(rawEmail);
        rejectIfAlreadyMember(groupId, email);

        groupInviteRepository.revokeActivePendingEmailInvites(groupId, email);

        User invitedBy = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String rawToken = InviteTokenUtil.generate();
        String tokenHash = TokenHashUtil.sha256(rawToken);

        GroupInvite invite = GroupInvite.createEmailInvite(
                group,
                email,
                tokenHash,
                invitedBy,
                LocalDateTime.now().plusDays(emailExpiryDays)
        );
        groupInviteRepository.save(invite);

        String joinLink = joinLinkBaseUrl + "?token=" + rawToken;
        emailService.sendInviteEmail(email, group.getName(), joinLink);
    }

    @Transactional
    public List<GroupInviteResponse> listInvites(Long groupId, Long requesterId) {
        groupAccessService.requireAdmin(groupId, requesterId);
        return groupInviteRepository.findByGroup_IdAndRevokedFalse(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revokeInvite(Long groupId, Long requesterId, Long inviteId) {
        groupAccessService.requireAdmin(groupId, requesterId);
        GroupInvite invite = groupInviteRepository.findByIdAndGroup_Id(inviteId, groupId)
                .orElseThrow(() -> new InviteNotFoundException("Invite not found"));
        invite.revoke();
    }

    @Transactional
    public GroupInviteResponse generateLink(Long groupId, Long requesterId) {
        groupAccessService.requireAdmin(groupId, requesterId);
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        groupInviteRepository.revokeActiveLinkInvites(groupId);

        User invitedBy = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String rawToken = InviteTokenUtil.generate();
        GroupInvite invite = GroupInvite.createLinkInvite(group, rawToken, invitedBy);
        invite = groupInviteRepository.save(invite);

        return toResponse(invite);
    }

    @Transactional
    public void disableLink(Long groupId, Long requesterId) {
        groupAccessService.requireAdmin(groupId, requesterId);
        groupInviteRepository.revokeActiveLinkInvites(groupId);
    }

    @Transactional
    public JoinInviteResponse joinViaInvite(Long userId, String rawToken) {
        GroupInvite invite = findInviteByRawOrHashedToken(rawToken)
                .orElseThrow(() -> new InviteNotFoundException("Invalid invite token"));

        validateInviteIsUsable(invite);

        Group group = invite.getGroup();
        if (group.isDeleted()) {
            throw new GroupNotFoundException("Group not found");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), userId)
                .orElse(null);

        if (membership != null && membership.isActive()) {
            throw new AlreadyGroupMemberException("You are already a member of this group");
        }

        if (membership != null) {
            membership.rejoin();
        } else {
            membership = GroupMember.createMember(group, user);
            groupMemberRepository.save(membership);
        }

        if (invite.getType() == GroupInviteType.EMAIL) {
            invite.accept();
        }

        return new JoinInviteResponse(
                group.getId(),
                group.getName(),
                membership.getRole(),
                "Joined group successfully"
        );
    }

    private Optional<GroupInvite> findInviteByRawOrHashedToken(String rawToken) {
        return groupInviteRepository.findByToken(rawToken)
                .or(() -> groupInviteRepository.findByToken(TokenHashUtil.sha256(rawToken)));
    }

    private void validateInviteIsUsable(GroupInvite invite) {
        if (invite.isRevoked()) {
            throw new InvalidInviteException("This invite has been revoked");
        }
        if (invite.isExpired()) {
            throw new InvalidInviteException("This invite has expired");
        }
        if (invite.getType() == GroupInviteType.EMAIL && invite.getAcceptedAt() != null) {
            throw new InvalidInviteException("This invite has already been used");
        }
    }

    private void rejectIfAlreadyMember(Long groupId, String email) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isEmpty()) {
            return;
        }
        boolean alreadyMember = groupMemberRepository
                .findByGroup_IdAndUser_IdAndLeftAtIsNull(groupId, existingUser.get().getId())
                .isPresent();
        if (alreadyMember) {
            throw new AlreadyGroupMemberException("This user is already a member of the group");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private GroupInviteResponse toResponse(GroupInvite invite) {
        String exposedToken = invite.getType() == GroupInviteType.LINK ? invite.getToken() : null;
        return new GroupInviteResponse(
                invite.getId(),
                invite.getType(),
                invite.getEmail(),
                exposedToken,
                invite.getExpiresAt(),
                invite.getAcceptedAt(),
                invite.isRevoked(),
                invite.getInvitedBy().getFullName(),
                invite.getCreatedAt()
        );
    }
}
