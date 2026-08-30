package com.billbuddy.backend.features.groups.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupInvite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupInviteRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupInviteRepository groupInviteRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Goa Trip", "desc", "INR", creator));
    }

    @Test
    void findByToken_findsExactMatch() {
        User admin = persistUser("admin@example.com");
        Group group = persistGroup(admin);
        groupInviteRepository.save(GroupInvite.createLinkInvite(group, "raw-token-value", admin));
        entityManager.flush();
        entityManager.clear();

        Optional<GroupInvite> result = groupInviteRepository.findByToken("raw-token-value");

        assertThat(result).isPresent();
    }

    @Test
    void findByIdAndGroup_Id_returnsEmpty_whenInviteBelongsToDifferentGroup() {
        User admin = persistUser("admin@example.com");
        Group groupA = persistGroup(admin);
        Group groupB = persistGroup(admin);
        GroupInvite invite = groupInviteRepository.save(GroupInvite.createLinkInvite(groupA, "raw-token", admin));
        entityManager.flush();
        entityManager.clear();

        Optional<GroupInvite> resultForOwnGroup = groupInviteRepository.findByIdAndGroup_Id(invite.getId(), groupA.getId());
        Optional<GroupInvite> resultForOtherGroup = groupInviteRepository.findByIdAndGroup_Id(invite.getId(), groupB.getId());

        assertThat(resultForOwnGroup).isPresent();
        assertThat(resultForOtherGroup).isEmpty();
    }

    @Test
    void findByGroup_IdAndRevokedFalse_excludesRevokedInvites() {
        User admin = persistUser("admin@example.com");
        Group group = persistGroup(admin);
        GroupInvite active = GroupInvite.createLinkInvite(group, "active-token", admin);
        GroupInvite revoked = GroupInvite.createLinkInvite(group, "revoked-token", admin);
        revoked.revoke();
        groupInviteRepository.save(active);
        groupInviteRepository.save(revoked);
        entityManager.flush();
        entityManager.clear();

        List<GroupInvite> result = groupInviteRepository.findByGroup_IdAndRevokedFalse(group.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getToken()).isEqualTo("active-token");
    }

    @Test
    void revokeActiveLinkInvites_onlyRevokesLinkType() {
        User admin = persistUser("admin@example.com");
        Group group = persistGroup(admin);
        groupInviteRepository.save(GroupInvite.createLinkInvite(group, "link-token", admin));
        groupInviteRepository.save(GroupInvite.createEmailInvite(
                group, "friend@example.com", "email-token-hash", admin, LocalDateTime.now().plusDays(7)
        ));
        entityManager.flush();

        int updated = groupInviteRepository.revokeActiveLinkInvites(group.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(groupInviteRepository.findByToken("link-token").orElseThrow().isRevoked()).isTrue();
        assertThat(groupInviteRepository.findByToken("email-token-hash").orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeActivePendingEmailInvites_onlyRevokesMatchingGroupAndEmail() {
        User admin = persistUser("admin@example.com");
        Group group = persistGroup(admin);
        groupInviteRepository.save(GroupInvite.createEmailInvite(
                group, "friend@example.com", "hash-for-friend", admin, LocalDateTime.now().plusDays(7)
        ));
        groupInviteRepository.save(GroupInvite.createEmailInvite(
                group, "other@example.com", "hash-for-other", admin, LocalDateTime.now().plusDays(7)
        ));
        entityManager.flush();

        int updated = groupInviteRepository.revokeActivePendingEmailInvites(group.getId(), "friend@example.com");
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(groupInviteRepository.findByToken("hash-for-friend").orElseThrow().isRevoked()).isTrue();
        assertThat(groupInviteRepository.findByToken("hash-for-other").orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeAllActiveInvites_revokesBothLinkAndEmailTypes() {
        User admin = persistUser("admin@example.com");
        Group group = persistGroup(admin);
        groupInviteRepository.save(GroupInvite.createLinkInvite(group, "link-token", admin));
        groupInviteRepository.save(GroupInvite.createEmailInvite(
                group, "friend@example.com", "email-token-hash", admin, LocalDateTime.now().plusDays(7)
        ));
        entityManager.flush();

        int updated = groupInviteRepository.revokeAllActiveInvites(group.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
    }
}
