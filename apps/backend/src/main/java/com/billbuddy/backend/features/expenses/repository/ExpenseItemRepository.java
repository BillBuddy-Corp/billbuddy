package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByExpense_Id(Long expenseId);

    // bulk delete executes immediately, unlike a derived deleteBy (deferred select-then-remove) -- the edit flow re-inserts new items right after this.
    @Modifying
    @Query("delete from ExpenseItem ei where ei.expense.id = :expenseId")
    void deleteByExpense_Id(Long expenseId);
}
