package com.billbuddy.backend.features.expenses.repository;

import com.billbuddy.backend.features.expenses.model.RecurringExpenseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, Long> {

    List<RecurringExpenseTemplate> findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId);

    Optional<RecurringExpenseTemplate> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        select t from RecurringExpenseTemplate t
        where t.active = true
          and t.deletedAt is null
          and t.nextRunAt <= CURRENT_DATE
    """)
    List<RecurringExpenseTemplate> findDueTemplates();
}
