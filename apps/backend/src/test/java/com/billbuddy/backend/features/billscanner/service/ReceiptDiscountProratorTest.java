package com.billbuddy.backend.features.billscanner.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptDiscountProratorTest {

    private ReceiptScannerService.ScannedLineItem item(String name, String amount) {
        return new ReceiptScannerService.ScannedLineItem(name, new BigDecimal(amount), 1, null);
    }

    private BigDecimal sum(List<ReceiptScannerService.ScannedLineItem> items) {
        return items.stream().map(ReceiptScannerService.ScannedLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===================== ALREADY RECONCILED =====================

    @Test
    void reconcileToTotal_leavesItemsUnchanged_whenSubtotalAlreadyMatchesTotal() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("Bread", "2.50"), item("Milk", "1.50"));

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("4.00"));

        assertThat(result.needsReview()).isFalse();
        assertThat(result.items()).isEqualTo(items);
    }

    @Test
    void reconcileToTotal_needsReviewFalse_whenSubtotalAndTotalAreBothZero() {
        // every item fully offset by its own coupon -- 0.00 == 0.00 is a legitimate match, not a failure
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("Free Sample", "0.00"));

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("0.00"));

        assertThat(result.needsReview()).isFalse();
        assertThat(sum(result.items())).isEqualByComparingTo("0.00");
    }

    // ===================== PRORATION =====================

    @Test
    void reconcileToTotal_scalesItemsProportionally_whenSubtotalDiffersFromTotal() {
        // a whole-receipt discount: items summed net of their own discounts don't yet reflect it
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("A", "60.00"), item("B", "40.00"));

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("90.00"));

        assertThat(result.needsReview()).isFalse();
        assertThat(sum(result.items())).isEqualByComparingTo("90.00");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("54.00");
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("36.00");
    }

    @Test
    void reconcileToTotal_distributesRemainderCent_toLargestItemFirst_regardlessOfListOrder() {
        // deliberately not sorted by size in the input list, to prove it's the amount that decides,
        // not position: subtotal=100.00, ratio=33.33/100=0.3333 leaves exactly 1 cent of rounding dust
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                item("Small", "10.00"), item("Big", "70.00"), item("Medium", "20.00")
        );

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("33.33"));

        assertThat(result.needsReview()).isFalse();
        assertThat(sum(result.items())).isEqualByComparingTo("33.33");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("3.33"); // Small
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("23.34"); // Big -- absorbs the extra cent
        assertThat(result.items().get(2).amount()).isEqualByComparingTo("6.66"); // Medium
    }

    @Test
    void reconcileToTotal_preservesNameAndQuantity_afterProration() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Onions", new BigDecimal("2.00"), 2, null)
        );

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("1.80"));

        assertThat(result.items().get(0).name()).isEqualTo("Onions");
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("1.80");
    }

    // ===================== NEEDS REVIEW =====================

    @Test
    void reconcileToTotal_needsReviewTrue_whenItemsNull() {
        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(null, new BigDecimal("10.00"));

        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void reconcileToTotal_needsReviewTrue_whenItemsEmpty() {
        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(List.of(), new BigDecimal("10.00"));

        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void reconcileToTotal_needsReviewTrue_whenTotalNull() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("Bread", "2.50"));

        ReceiptDiscountProrator.ReconciliationResult result = ReceiptDiscountProrator.reconcileToTotal(items, null);

        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void reconcileToTotal_needsReviewTrue_whenSubtotalIsZeroButTotalIsNot() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("Free Sample", "0.00"));

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("5.00"));

        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void reconcileToTotal_needsReviewTrue_whenSubtotalIsNegative() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(item("Broken Read", "-3.00"));

        ReceiptDiscountProrator.ReconciliationResult result =
                ReceiptDiscountProrator.reconcileToTotal(items, new BigDecimal("5.00"));

        assertThat(result.needsReview()).isTrue();
    }
}
