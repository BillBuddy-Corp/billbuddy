package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpenseRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Goa Trip", "desc", "INR", creator));
    }

    private Expense persistExpense(Group group, User creator, BigDecimal amount) {
        return expenseRepository.save(Expense.create(
                group, creator, "Dinner", amount, "INR", amount, BigDecimal.ONE, null, null, SplitType.EQUAL
        ));
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludesSoftDeletedExpense() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("50"));
        expense.softDelete();
        expenseRepository.save(expense);
        entityManager.flush();
        entityManager.clear();

        Optional<Expense> result = expenseRepository.findByIdAndDeletedAtIsNull(expense.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc_excludesDeletedAndOrdersByNewestFirst() throws InterruptedException {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);

        Expense first = persistExpense(group, user, new BigDecimal("10"));
        Thread.sleep(5); // ensure a distinct created_at ordering between inserts
        Expense second = persistExpense(group, user, new BigDecimal("20"));

        Expense deleted = persistExpense(group, user, new BigDecimal("30"));
        deleted.softDelete();
        expenseRepository.save(deleted);

        entityManager.flush();
        entityManager.clear();

        List<Expense> result = expenseRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(group.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(second.getId());
        assertThat(result.get(1).getId()).isEqualTo(first.getId());
    }
}
