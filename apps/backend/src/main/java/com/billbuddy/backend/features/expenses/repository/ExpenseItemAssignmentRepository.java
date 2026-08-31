package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.ExpenseItemAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseItemAssignmentRepository extends JpaRepository<ExpenseItemAssignment, Long> {

    List<ExpenseItemAssignment> findByExpenseItem_Id(Long expenseItemId);

    // deletes all assignments for every item belonging to the expense; bulk delete executes immediately so the subsequent item delete doesn't hit a stale FK reference.
    @Modifying
    @Query("delete from ExpenseItemAssignment eia where eia.expenseItem.expense.id = :expenseId")
    void deleteByExpenseItem_Expense_Id(Long expenseId);
}
