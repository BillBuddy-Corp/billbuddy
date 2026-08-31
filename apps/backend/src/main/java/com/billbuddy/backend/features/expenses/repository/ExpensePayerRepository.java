package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.ExpensePayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpensePayerRepository extends JpaRepository<ExpensePayer, Long> {

    List<ExpensePayer> findByExpense_Id(Long expenseId);

    // bulk delete executes immediately, unlike a derived deleteBy (deferred select-then-remove) -- the edit flow re-inserts the same unique key right after this.
    @Modifying
    @Query("delete from ExpensePayer ep where ep.expense.id = :expenseId")
    void deleteByExpense_Id(Long expenseId);
}
