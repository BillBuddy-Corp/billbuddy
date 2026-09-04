package com.billbuddy.backend.features.billscanner.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Reconciles extracted line items to the receipt's actual total, using the same
// floor-then-distribute-remainder technique as ExpenseSplitCalculator. This is what makes a
// receipt-level discount (e.g. a loyalty discount not tied to any single item) land correctly:
// items are already net of any item-specific discount (see ReceiptItemNormalizer), and this
// rescales those proportionally so they sum exactly to the real total -- math the model itself
// shouldn't be trusted to do reliably across many line items. needsReview signals that the gap
// between the raw item sum and the total was larger than simple cent-rounding could explain --
// evidence the extraction itself is off, not just a rounding artifact.
public final class ReceiptDiscountProrator {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    private ReceiptDiscountProrator() {
        // prevent instantiation
    }

    public record ReconciliationResult(List<ReceiptScannerService.ScannedLineItem> items, boolean needsReview) {
    }

    public static ReconciliationResult reconcileToTotal(
            List<ReceiptScannerService.ScannedLineItem> items, BigDecimal total
    ) {
        if (items == null || items.isEmpty()) {
            return new ReconciliationResult(items, true); // nothing extracted -- can't verify anything
        }
        if (total == null) {
            return new ReconciliationResult(items, true); // no total to reconcile against
        }

        BigDecimal subtotal = items.stream()
                .map(ReceiptScannerService.ScannedLineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal scaledTotal = total.setScale(2, RoundingMode.HALF_UP);
        if (subtotal.setScale(2, RoundingMode.HALF_UP).compareTo(scaledTotal) == 0) {
            return new ReconciliationResult(items, false); // already reconciled, nothing to prorate -- covers 0.00 == 0.00 too
        }
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new ReconciliationResult(items, true); // items don't add up to anything positive, and doesn't match total either
        }

        BigDecimal ratio = total.divide(subtotal, 10, RoundingMode.HALF_UP);

        List<BigDecimal> floors = new ArrayList<>();
        BigDecimal sumFloors = BigDecimal.ZERO;
        for (ReceiptScannerService.ScannedLineItem item : items) {
            BigDecimal floor = item.amount().multiply(ratio).setScale(2, RoundingMode.FLOOR);
            floors.add(floor);
            sumFloors = sumFloors.add(floor);
        }

        BigDecimal remainder = scaledTotal.subtract(sumFloors);
        int remainderCents;
        try {
            remainderCents = remainder.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException ex) {
            return new ReconciliationResult(items, true); // extracted numbers didn't reconcile cleanly
        }

        int n = items.size();
        if (remainderCents < 0 || remainderCents >= n) {
            return new ReconciliationResult(items, true); // outside the expected rounding-dust range
        }

        // largest items absorb the rounding dust first -- proportionally negligible for them
        List<Integer> orderByAmountDesc = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orderByAmountDesc.add(i);
        }
        orderByAmountDesc.sort(Comparator.<Integer, BigDecimal>comparing(i -> items.get(i).amount()).reversed());

        for (int i = 0; i < remainderCents; i++) {
            int index = orderByAmountDesc.get(i);
            floors.set(index, floors.get(index).add(ONE_CENT));
        }

        List<ReceiptScannerService.ScannedLineItem> reconciled = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            reconciled.add(new ReceiptScannerService.ScannedLineItem(items.get(i).name(), floors.get(i), items.get(i).quantity(), null));
        }
        return new ReconciliationResult(reconciled, false);
    }
}
