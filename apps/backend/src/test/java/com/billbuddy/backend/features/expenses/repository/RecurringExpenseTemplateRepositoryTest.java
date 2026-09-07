package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.RecurringExpenseTemplate;
import com.billbuddy.backend.features.expenses.model.RecurringFrequency;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecurringExpenseTemplateRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RecurringExpenseTemplateRepository templateRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Trip", "desc", "INR", creator));
    }

    private RecurringExpenseTemplate saveTemplate(Group group, User creator, LocalDate nextRunAt, boolean active) {
        RecurringExpenseTemplate template = RecurringExpenseTemplate.create(
                group, creator, "Rent", new java.math.BigDecimal("90"), "INR", null, null,
                SplitType.EQUAL, RecurringFrequency.MONTHLY, null, 1,
                "{\"payers\":[{\"userId\":1,\"amountPaid\":90}],\"participantUserIds\":[1]}", nextRunAt
        );
        if (!active) {
            template.pause();
        }
        return templateRepository.save(template);
    }

    @Test
    void findByGroup_returnsTemplatesOrderedByCreatedAtDesc() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        saveTemplate(group, user, LocalDate.now().plusDays(1), true);
        entityManager.flush();
        entityManager.clear();

        List<RecurringExpenseTemplate> result = templateRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(group.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSplitConfig()).contains("participantUserIds");
    }

    @Test
    void findDueTemplates_returnsOnlyActiveAndDue() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        RecurringExpenseTemplate due = saveTemplate(group, user, LocalDate.now().minusDays(1), true);
        saveTemplate(group, user, LocalDate.now().plusDays(5), true); // not due yet
        RecurringExpenseTemplate pausedButDue = saveTemplate(group, user, LocalDate.now().minusDays(1), false);
        entityManager.flush();
        entityManager.clear();

        List<RecurringExpenseTemplate> result = templateRepository.findDueTemplates();

        assertThat(result).extracting(RecurringExpenseTemplate::getId)
                .containsExactly(due.getId())
                .doesNotContain(pausedButDue.getId());
    }

    @Test
    void findDueTemplates_excludesCancelledTemplates() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        RecurringExpenseTemplate cancelled = saveTemplate(group, user, LocalDate.now().minusDays(1), true);
        cancelled.cancel();
        templateRepository.save(cancelled);
        entityManager.flush();
        entityManager.clear();

        List<RecurringExpenseTemplate> result = templateRepository.findDueTemplates();

        assertThat(result).isEmpty();
    }
}
