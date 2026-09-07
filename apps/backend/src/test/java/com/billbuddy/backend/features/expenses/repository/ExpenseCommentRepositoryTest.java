package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseComment;
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
class ExpenseCommentRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseCommentRepository expenseCommentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Trip", "desc", "INR", creator));
    }

    private Expense persistExpense(Group group, User creator) {
        return expenseRepository.save(Expense.create(
                group, creator, "Dinner", new BigDecimal("90"), "INR",
                new BigDecimal("90"), BigDecimal.ONE, null, null, SplitType.EQUAL
        ));
    }

    @Test
    void findByExpense_returnsCommentsOldestFirst() throws InterruptedException {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user);
        ExpenseComment first = expenseCommentRepository.save(ExpenseComment.create(expense, user, "First"));
        entityManager.flush();
        Thread.sleep(5); // ensure a distinct created_at ordering between inserts
        ExpenseComment second = expenseCommentRepository.save(ExpenseComment.create(expense, user, "Second"));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseComment> result = expenseCommentRepository.findByExpense_IdOrderByCreatedAtAsc(expense.getId());

        assertThat(result).extracting(ExpenseComment::getId).containsExactly(first.getId(), second.getId());
        assertThat(result).extracting(ExpenseComment::getBody).containsExactly("First", "Second");
    }

    @Test
    void findByExpense_returnsEmpty_whenNoComments() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user);
        entityManager.flush();
        entityManager.clear();

        List<ExpenseComment> result = expenseCommentRepository.findByExpense_IdOrderByCreatedAtAsc(expense.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByExpense_scopesToOneExpense() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expenseA = persistExpense(group, user);
        Expense expenseB = persistExpense(group, user);
        expenseCommentRepository.save(ExpenseComment.create(expenseA, user, "On A"));
        expenseCommentRepository.save(ExpenseComment.create(expenseB, user, "On B"));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseComment> result = expenseCommentRepository.findByExpense_IdOrderByCreatedAtAsc(expenseA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBody()).isEqualTo("On A");
    }
}
