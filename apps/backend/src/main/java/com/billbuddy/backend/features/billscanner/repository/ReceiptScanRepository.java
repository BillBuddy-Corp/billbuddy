package com.billbuddy.backend.features.billscanner.repository;

import com.billbuddy.backend.features.billscanner.model.ReceiptScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptScanRepository extends JpaRepository<ReceiptScan, Long> {

    Optional<ReceiptScan> findByFile_Id(Long fileId);
}
