package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    List<ExpenseSplit> findByExpense_Id(Long expenseId);

    List<ExpenseSplit> findByExpense_Group_IdAndExpense_DeletedAtIsNull(Long groupId);

    List<ExpenseSplit> findByExpense_FriendUserLowIdAndExpense_FriendUserHighIdAndExpense_DeletedAtIsNull(
            Long friendUserLowId, Long friendUserHighId
    );

    // bulk delete executes immediately, unlike a derived deleteBy (deferred select-then-remove) -- the edit flow re-inserts the same unique key right after this.
    @Modifying
    @Query("delete from ExpenseSplit es where es.expense.id = :expenseId")
    void deleteByExpense_Id(Long expenseId);
}
