package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndDeletedAtIsNull(Long id);

    List<Expense> findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId);

    List<Expense> findByReceiptFile_Id(Long fileId);
}
