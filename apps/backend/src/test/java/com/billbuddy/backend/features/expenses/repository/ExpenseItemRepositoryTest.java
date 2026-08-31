package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.expenses.model.Expense;
import com.billbuddy.backend.features.expenses.model.ExpenseItem;
import com.billbuddy.backend.features.expenses.model.ExpenseItemAssignment;
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
class ExpenseItemRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseItemRepository expenseItemRepository;

    @Autowired
    private ExpenseItemAssignmentRepository expenseItemAssignmentRepository;

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
                group, creator, "Restaurant", amount, "INR", amount, BigDecimal.ONE, null, null, SplitType.ITEMIZED
        ));
    }

    @Test
    void findByExpenseItem_Id_returnsAssignmentsForThatItem() {
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        ExpenseItem item = expenseItemRepository.save(ExpenseItem.create(expense, "Pizza", new BigDecimal("100")));
        expenseItemAssignmentRepository.save(ExpenseItemAssignment.create(item, user, BigDecimal.ONE));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseItemAssignment> result = expenseItemAssignmentRepository.findByExpenseItem_Id(item.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void deletingAssignmentsBeforeItems_thenReinsertingImmediately_worksWithoutFkOrConstraintViolations() {
        // regression coverage for the exact FK-safe delete order ExpenseService.updateExpense
        // relies on: assignments must be gone (immediately, not deferred) before their item is
        // deleted, and the item's own delete must be immediate too so a same-named row can be
        // reinserted right after within the same transaction.
        User user = persistUser("jane@example.com");
        Group group = persistGroup(user);
        Expense expense = persistExpense(group, user, new BigDecimal("100"));
        ExpenseItem item = expenseItemRepository.save(ExpenseItem.create(expense, "Pizza", new BigDecimal("100")));
        expenseItemAssignmentRepository.save(ExpenseItemAssignment.create(item, user, BigDecimal.ONE));
        entityManager.flush();

        expenseItemAssignmentRepository.deleteByExpenseItem_Expense_Id(expense.getId());
        expenseItemRepository.deleteByExpense_Id(expense.getId());

        ExpenseItem newItem = expenseItemRepository.save(ExpenseItem.create(expense, "Pizza", new BigDecimal("100")));
        expenseItemAssignmentRepository.save(ExpenseItemAssignment.create(newItem, user, BigDecimal.ONE));
        entityManager.flush();
        entityManager.clear();

        List<ExpenseItem> items = expenseItemRepository.findByExpense_Id(expense.getId());
        assertThat(items).hasSize(1);
        assertThat(expenseItemAssignmentRepository.findByExpenseItem_Id(items.get(0).getId())).hasSize(1);
    }

    @Test
    void deleteByExpenseItem_Expense_Id_onlyRemovesAssignmentsForItemsOfThatExpense() {
        User user = persistUser("jane@example.com");
        Group groupA = persistGroup(user);
        Group groupB = persistGroup(user);
        Expense expenseA = persistExpense(groupA, user, new BigDecimal("100"));
        Expense expenseB = persistExpense(groupB, user, new BigDecimal("50"));

        ExpenseItem itemA = expenseItemRepository.save(ExpenseItem.create(expenseA, "Pizza", new BigDecimal("100")));
        ExpenseItem itemB = expenseItemRepository.save(ExpenseItem.create(expenseB, "Coke", new BigDecimal("50")));
        expenseItemAssignmentRepository.save(ExpenseItemAssignment.create(itemA, user, BigDecimal.ONE));
        expenseItemAssignmentRepository.save(ExpenseItemAssignment.create(itemB, user, BigDecimal.ONE));
        entityManager.flush();

        expenseItemAssignmentRepository.deleteByExpenseItem_Expense_Id(expenseA.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(expenseItemAssignmentRepository.findByExpenseItem_Id(itemA.getId())).isEmpty();
        assertThat(expenseItemAssignmentRepository.findByExpenseItem_Id(itemB.getId())).hasSize(1);
    }
}
