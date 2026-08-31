package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpensePayerRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpensePayerRepository expensePayerRepository;

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
    void findByExpense_Id_returnsAllPayersForThatExpense() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        expensePayerRepository.save(ExpensePayer.create(expense, user, new BigDecimal("100")));
        entityManager.flush();
        entityManager.clear();

        List<ExpensePayer> result = expensePayerRepository.findByExpense_Id(expense.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void uniqueConstraint_rejectsDuplicatePayerRowForSameExpenseAndUser() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        expensePayerRepository.save(ExpensePayer.create(expense, user, new BigDecimal("100")));
        entityManager.flush();

        assertThatThrownBy(() -> {
            expensePayerRepository.save(ExpensePayer.create(expense, user, new BigDecimal("50")));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByExpense_Id_executesImmediately_soARowCanBeReinsertedRightAfter() {
        // regression test for the bug this exact scenario surfaced: a derived deleteBy
        // (select-then-remove, deferred to flush) let a stale row collide with a
        // same-key insert performed right after it, within the same transaction.
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        expensePayerRepository.save(ExpensePayer.create(expense, user, new BigDecimal("100")));
        entityManager.flush();

        expensePayerRepository.deleteByExpense_Id(expense.getId());
        // no explicit flush/clear here -- deliberately re-inserting immediately after
        // the delete call, the same way ExpenseService.updateExpense does.
        expensePayerRepository.save(ExpensePayer.create(expense, user, new BigDecimal("100")));
        entityManager.flush();
        entityManager.clear();

        List<ExpensePayer> result = expensePayerRepository.findByExpense_Id(expense.getId());
        assertThat(result).hasSize(1);
    }
}
