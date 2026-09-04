package com.billbuddy.backend.features.billscanner.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptItemNormalizerTest {

    // ===================== NULL / EMPTY =====================

    @Test
    void normalize_returnsNull_whenItemsNull() {
        assertThat(ReceiptItemNormalizer.normalize(null)).isNull();
    }

    @Test
    void normalize_returnsEmpty_whenItemsEmpty() {
        assertThat(ReceiptItemNormalizer.normalize(List.of())).isEmpty();
    }

    // ===================== ITEM DISCOUNT NETTING =====================

    @Test
    void normalize_netsItemDiscount_intoAmount() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Onions", new BigDecimal("2.98"), 2, new BigDecimal("1.50"))
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("1.48");
        assertThat(result.get(0).itemDiscount()).isNull(); // spent, no longer relevant downstream
    }

    @Test
    void normalize_leavesAmountUnchanged_whenNoItemDiscount() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Bread", new BigDecimal("2.10"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result.get(0).amount()).isEqualByComparingTo("2.10");
    }

    @Test
    void normalize_clampsNetAmountToZero_whenItemDiscountExceedsAmount() {
        // a misread itemDiscount larger than the item's own price -- an item can become free, never negative
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Scallions", new BigDecimal("0.79"), 1, new BigDecimal("1.00"))
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result.get(0).amount()).isEqualByComparingTo("0.00");
    }

    // ===================== NEGATIVE-AMOUNT FOLDING =====================

    @Test
    void normalize_foldsNegativeAmountItem_intoPrecedingItem() {
        // a discount line Claude reported as its own fake "item" instead of using itemDiscount
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Cob", new BigDecimal("1.79"), 1, null),
                new ReceiptScannerService.ScannedLineItem("Coupon Plus reward", new BigDecimal("-1.79"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Cob");
        assertThat(result.get(0).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void normalize_dropsNegativeAmountItem_whenNothingPrecedesIt() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Stray Discount", new BigDecimal("-2.00"), 1, null),
                new ReceiptScannerService.ScannedLineItem("Bread", new BigDecimal("2.10"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bread");
    }

    // ===================== DUPLICATE MERGING =====================

    @Test
    void normalize_mergesDuplicateNames_summingAmountsAndQuantities() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Fruit & Barley 1L", new BigDecimal("0.65"), 1, null),
                new ReceiptScannerService.ScannedLineItem("Fruit & Barley 1L", new BigDecimal("0.65"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("1.30");
        assertThat(result.get(0).quantity()).isEqualTo(2);
    }

    @Test
    void normalize_mergeIsCaseInsensitiveAndTrimmed() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Onions", new BigDecimal("1.00"), 1, null),
                new ReceiptScannerService.ScannedLineItem("  onions  ", new BigDecimal("1.00"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("2.00");
        assertThat(result.get(0).quantity()).isEqualTo(2);
        assertThat(result.get(0).name()).isEqualTo("Onions"); // keeps the first occurrence's casing
    }

    @Test
    void normalize_defaultsMissingQuantityToOne_whenMerging() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Midget Gems", new BigDecimal("0.29"), null, null),
                new ReceiptScannerService.ScannedLineItem("Midget Gems", new BigDecimal("0.29"), null, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result.get(0).quantity()).isEqualTo(2);
    }

    @Test
    void normalize_leavesDistinctItemsSeparate() {
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Bread", new BigDecimal("2.10"), 1, null),
                new ReceiptScannerService.ScannedLineItem("Milk", new BigDecimal("1.85"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(2);
    }

    // ===================== ORDERING OF OPERATIONS =====================

    @Test
    void normalize_netsDiscountBeforeMerging_soEachDuplicateLineKeepsItsOwnDiscount() {
        // one copy of a repeated item has its own discount, the other doesn't -- each must be netted
        // independently before merging, since a single item-wide discount would be wrong here
        List<ReceiptScannerService.ScannedLineItem> items = List.of(
                new ReceiptScannerService.ScannedLineItem("Everest Pudding", new BigDecimal("2.00"), 1, new BigDecimal("0.51")),
                new ReceiptScannerService.ScannedLineItem("Everest Pudding", new BigDecimal("2.00"), 1, null)
        );

        List<ReceiptScannerService.ScannedLineItem> result = ReceiptItemNormalizer.normalize(items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("3.49"); // 1.49 + 2.00
        assertThat(result.get(0).quantity()).isEqualTo(2);
    }
}
