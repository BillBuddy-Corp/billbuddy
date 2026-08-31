package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpenseSplitRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

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
    void findByExpense_Id_returnsAllSplitsForThatExpense() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        expenseSplitRepository.save(ExpenseSplit.create(expense, user, new BigDecimal("100"), null));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseSplit> result = expenseSplitRepository.findByExpense_Id(expense.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmountOwed()).isEqualByComparingTo("100");
    }

    @Test
    void deleteByExpense_Id_executesImmediately_soARowCanBeReinsertedRightAfter() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        expenseSplitRepository.save(ExpenseSplit.create(expense, user, new BigDecimal("100"), null));
        entityManager.flush();

        expenseSplitRepository.deleteByExpense_Id(expense.getId());
        expenseSplitRepository.save(ExpenseSplit.create(expense, user, new BigDecimal("100"), null));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseSplit> result = expenseSplitRepository.findByExpense_Id(expense.getId());
        assertThat(result).hasSize(1);
    }
}
