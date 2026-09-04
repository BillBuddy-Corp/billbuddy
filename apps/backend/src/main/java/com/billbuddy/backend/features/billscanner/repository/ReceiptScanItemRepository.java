package com.billbuddy.backend.features.billscanner.repository;

import com.billbuddy.backend.features.billscanner.model.ReceiptScanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptScanItemRepository extends JpaRepository<ReceiptScanItem, Long> {

    List<ReceiptScanItem> findByReceiptScan_Id(Long receiptScanId);
}
