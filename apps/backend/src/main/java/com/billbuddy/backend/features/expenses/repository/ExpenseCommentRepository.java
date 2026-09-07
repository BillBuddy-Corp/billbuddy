package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.ExpenseComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCommentRepository extends JpaRepository<ExpenseComment, Long> {

    List<ExpenseComment> findByExpense_IdOrderByCreatedAtAsc(Long expenseId);
}
