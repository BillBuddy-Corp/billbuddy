package com.billbuddy.backend.features.groups.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.model.GroupMember;
import com.billbuddy.backend.features.groups.model.GroupRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupMemberRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Goa Trip", "desc", "INR", creator));
    }

    @Test
    void uniqueConstraint_rejectsDuplicateRowForSameGroupAndUser() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);

        groupMemberRepository.save(GroupMember.createAdmin(group, user));
        entityManager.flush();

        // IDENTITY generation inserts immediately on save(), not on a later flush().
        assertThatThrownBy(() -> {
            groupMemberRepository.save(GroupMember.createMember(group, user));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByGroup_IdAndUser_IdAndLeftAtIsNull_excludesLeftMembers() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        GroupMember member = GroupMember.createMember(group, user);
        member.leave();
        groupMemberRepository.save(member);
        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result = groupMemberRepository
                .findByGroup_IdAndUser_IdAndLeftAtIsNull(group.getId(), user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByGroup_IdAndUser_Id_returnsRowRegardlessOfLeftState() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        GroupMember member = GroupMember.createMember(group, user);
        member.leave();
        groupMemberRepository.save(member);
        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().isActive()).isFalse();
    }

    @Test
    void findByGroup_IdAndLeftAtIsNull_listsOnlyActiveMembers() {
        User admin = persistUser("admin@example.com");
        User activeMember = persistUser("active@example.com");
        User leftMember = persistUser("left@example.com");
        Group group = persistGroup(admin);

        groupMemberRepository.save(GroupMember.createAdmin(group, admin));
        groupMemberRepository.save(GroupMember.createMember(group, activeMember));
        GroupMember left = GroupMember.createMember(group, leftMember);
        left.leave();
        groupMemberRepository.save(left);

        entityManager.flush();
        entityManager.clear();

        List<GroupMember> activeMembers = groupMemberRepository.findByGroup_IdAndLeftAtIsNull(group.getId());

        assertThat(activeMembers).hasSize(2);
    }

    @Test
    void findByUser_IdAndLeftAtIsNull_listsUsersActiveGroups() {
        User user = persistUser("jane@example.com");
        Group activeGroup = persistGroup(user);
        Group leftGroup = persistGroup(user);

        groupMemberRepository.save(GroupMember.createAdmin(activeGroup, user));
        GroupMember leftMembership = GroupMember.createAdmin(leftGroup, user);
        leftMembership.leave();
        groupMemberRepository.save(leftMembership);

        entityManager.flush();
        entityManager.clear();

        List<GroupMember> result = groupMemberRepository.findByUser_IdAndLeftAtIsNull(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroup().getId()).isEqualTo(activeGroup.getId());
    }

    @Test
    void countByGroup_IdAndLeftAtIsNull_countsOnlyActive() {
        User admin = persistUser("admin@example.com");
        User leftMember = persistUser("left@example.com");
        Group group = persistGroup(admin);

        groupMemberRepository.save(GroupMember.createAdmin(group, admin));
        GroupMember left = GroupMember.createMember(group, leftMember);
        left.leave();
        groupMemberRepository.save(left);

        entityManager.flush();
        entityManager.clear();

        long count = groupMemberRepository.countByGroup_IdAndLeftAtIsNull(group.getId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByGroup_IdAndRoleAndLeftAtIsNull_countsOnlyMatchingActiveRole() {
        User admin = persistUser("admin@example.com");
        User member = persistUser("member@example.com");
        Group group = persistGroup(admin);

        groupMemberRepository.save(GroupMember.createAdmin(group, admin));
        groupMemberRepository.save(GroupMember.createMember(group, member));

        entityManager.flush();
        entityManager.clear();

        long adminCount = groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(group.getId(), GroupRole.ADMIN);
        long memberCount = groupMemberRepository.countByGroup_IdAndRoleAndLeftAtIsNull(group.getId(), GroupRole.MEMBER);

        assertThat(adminCount).isEqualTo(1);
        assertThat(memberCount).isEqualTo(1);
    }
}
