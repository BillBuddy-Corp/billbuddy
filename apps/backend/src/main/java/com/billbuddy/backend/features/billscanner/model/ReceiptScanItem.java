package com.billbuddy.backend.features.billscanner.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "receipt_scan_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptScanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_scan_id", nullable = false)
    private ReceiptScan receiptScan;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer quantity;

    public static ReceiptScanItem create(ReceiptScan receiptScan, String name, BigDecimal amount, Integer quantity) {
        ReceiptScanItem item = new ReceiptScanItem();
        item.receiptScan = receiptScan;
        item.name = name;
        item.amount = amount;
        item.quantity = quantity;
        return item;
    }
}
