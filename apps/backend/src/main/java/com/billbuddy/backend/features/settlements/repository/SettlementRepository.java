package com.billbuddy.backend.features.settlements.repository;

import com.billbuddy.backend.features.settlements.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId);

    Optional<Settlement> findByIdAndDeletedAtIsNull(Long id);

    List<Settlement> findByGroup_IdAndDeletedAtIsNull(Long groupId);
}
