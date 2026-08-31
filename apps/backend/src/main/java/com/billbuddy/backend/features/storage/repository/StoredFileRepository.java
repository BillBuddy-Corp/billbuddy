package com.billbuddy.backend.features.storage.repository;

import com.billbuddy.backend.features.storage.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByIdAndDeletedAtIsNull(Long id);
}
