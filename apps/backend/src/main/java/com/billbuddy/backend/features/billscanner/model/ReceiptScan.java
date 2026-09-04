package com.billbuddy.backend.features.billscanner.model;

import com.billbuddy.backend.features.storage.model.StoredFile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipt_scans")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private StoredFile file;

    @Column
    private String merchant;

    @Column
    private BigDecimal amount;

    @Column
    private String currency;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "other_discount")
    private BigDecimal otherDiscount;

    @Column(name = "voucher_amount")
    private BigDecimal voucherAmount;

    @Column
    private BigDecimal subtotal;

    // Whether the item prices themselves can be trusted -- true only when they couldn't be
    // reconciled to add up to amount at all. This is what actually matters for splitting the bill.
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    // Whether the informational discount breakdown (otherDiscount/subtotal) looks inconsistent --
    // independent of needsReview, since item prices always reconcile to amount regardless.
    @Column(name = "discounts_need_review", nullable = false)
    private boolean discountsNeedReview;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static ReceiptScan create(
            StoredFile file,
            String merchant,
            BigDecimal amount,
            String currency,
            LocalDate transactionDate,
            BigDecimal otherDiscount,
            BigDecimal voucherAmount,
            BigDecimal subtotal,
            boolean needsReview,
            boolean discountsNeedReview
    ) {
        ReceiptScan scan = new ReceiptScan();
        scan.file = file;
        scan.merchant = merchant;
        scan.amount = amount;
        scan.currency = currency;
        scan.transactionDate = transactionDate;
        scan.otherDiscount = otherDiscount;
        scan.voucherAmount = voucherAmount;
        scan.subtotal = subtotal;
        scan.needsReview = needsReview;
        scan.discountsNeedReview = discountsNeedReview;
        return scan;
    }
}
