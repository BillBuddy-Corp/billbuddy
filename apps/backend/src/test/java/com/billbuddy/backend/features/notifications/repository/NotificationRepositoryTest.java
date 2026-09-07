package com.billbuddy.backend.features.notifications.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.notifications.model.Notification;
import com.billbuddy.backend.features.notifications.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Trip", "desc", "INR", creator));
    }

    @Test
    void findByUser_returnsNewestFirst() throws InterruptedException {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Notification first = notificationRepository.save(
                Notification.create(user, NotificationType.EXPENSE_CREATED, "First", group, null, null));
        entityManager.flush();
        Thread.sleep(5); // ensure a distinct created_at ordering between inserts
        Notification second = notificationRepository.save(
                Notification.create(user, NotificationType.COMMENT_POSTED, "Second", group, null, null));
        entityManager.flush();
        entityManager.clear();

        List<Notification> result = notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        assertThat(result).extracting(Notification::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void countByUser_countsOnlyUnread() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Notification unread = notificationRepository.save(
                Notification.create(user, NotificationType.EXPENSE_CREATED, "Unread", group, null, null));
        Notification read = notificationRepository.save(
                Notification.create(user, NotificationType.EXPENSE_CREATED, "Read", group, null, null));
        read.markRead();
        notificationRepository.save(read);
        entityManager.flush();
        entityManager.clear();

        long count = notificationRepository.countByUser_IdAndReadAtIsNull(user.getId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void findByIdAndUser_returnsEmpty_whenNotificationBelongsToDifferentUser() {
        User owner = persistUser("jane@example.com");
        User other = persistUser("bob@example.com");
        Group group = persistGroup(owner);
        Notification notification = notificationRepository.save(
                Notification.create(owner, NotificationType.EXPENSE_CREATED, "Mine", group, null, null));
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> result = notificationRepository.findByIdAndUser_Id(notification.getId(), other.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void markAllAsRead_marksOnlyThatUsersUnreadNotifications() {
        User user = persistUser("jane@example.com");
        User otherUser = persistUser("bob@example.com");
        Group group = persistGroup(user);
        Notification mine = notificationRepository.save(
                Notification.create(user, NotificationType.EXPENSE_CREATED, "Mine", group, null, null));
        Notification theirs = notificationRepository.save(
                Notification.create(otherUser, NotificationType.EXPENSE_CREATED, "Theirs", group, null, null));
        entityManager.flush();
        entityManager.clear();

        int updated = notificationRepository.markAllAsRead(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(notificationRepository.findById(mine.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(theirs.getId()).orElseThrow().isRead()).isFalse();
    }
}
