package com.billbuddy.backend.features.groups.repository;

import com.billbuddy.backend.features.groups.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByIdAndDeletedAtIsNull(Long id);
}
