package com.billbuddy.backend.features.billscanner.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

// Applies deterministically what Claude only identifies, never computes: folds any item Claude
// mistakenly reported as its own negative-priced entry (a discount or coupon line it failed to
// attach via itemDiscount) into the item before it, nets each item's own printed discount into
// its price, then merges lines that are the same item repeated verbatim (matched by normalized
// name) into one entry with a combined quantity. All three used to be asked of the model
// directly and were unreliable often enough (a discount line reported as a fake item, a missed
// discount netting, an unmerged duplicate line) that the arithmetic belongs here instead.
public final class ReceiptItemNormalizer {

    private ReceiptItemNormalizer() {
    }

    public static List<ReceiptScannerService.ScannedLineItem> normalize(List<ReceiptScannerService.ScannedLineItem> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        return mergeDuplicates(netItemDiscounts(foldNegativeAmountsIntoPrevious(items)));
    }

    // A real item's printed price is never negative -- if Claude reports one, it's almost always
    // a discount/coupon line it failed to attach to the item above via itemDiscount instead.
    private static List<ReceiptScannerService.ScannedLineItem> foldNegativeAmountsIntoPrevious(List<ReceiptScannerService.ScannedLineItem> items) {
        List<ReceiptScannerService.ScannedLineItem> folded = new ArrayList<>();
        for (ReceiptScannerService.ScannedLineItem item : items) {
            if (item.amount().compareTo(BigDecimal.ZERO) < 0 && !folded.isEmpty()) {
                ReceiptScannerService.ScannedLineItem previous = folded.remove(folded.size() - 1);
                BigDecimal adjusted = previous.amount().add(item.amount());
                folded.add(new ReceiptScannerService.ScannedLineItem(previous.name(), adjusted, previous.quantity(), previous.itemDiscount()));
            } else if (item.amount().compareTo(BigDecimal.ZERO) >= 0) {
                folded.add(item);
            }
            // a negative amount with nothing preceding it can't be attributed -- drop it rather than guess
        }
        return folded;
    }

    private static List<ReceiptScannerService.ScannedLineItem> netItemDiscounts(List<ReceiptScannerService.ScannedLineItem> items) {
        List<ReceiptScannerService.ScannedLineItem> netted = new ArrayList<>();
        for (ReceiptScannerService.ScannedLineItem item : items) {
            BigDecimal net = item.itemDiscount() != null ? item.amount().subtract(item.itemDiscount()) : item.amount();
            // an item can become free but never negative -- an overshooting itemDiscount is a misread, not a rebate
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                net = BigDecimal.ZERO;
            }
            netted.add(new ReceiptScannerService.ScannedLineItem(item.name(), net, item.quantity(), null));
        }
        return netted;
    }

    private static List<ReceiptScannerService.ScannedLineItem> mergeDuplicates(List<ReceiptScannerService.ScannedLineItem> items) {
        LinkedHashMap<String, ReceiptScannerService.ScannedLineItem> merged = new LinkedHashMap<>();
        for (ReceiptScannerService.ScannedLineItem item : items) {
            String key = item.name().trim().toLowerCase();
            ReceiptScannerService.ScannedLineItem existing = merged.get(key);
            if (existing == null) {
                merged.put(key, item);
            } else {
                int quantity = quantityOrOne(existing) + quantityOrOne(item);
                BigDecimal amount = existing.amount().add(item.amount());
                merged.put(key, new ReceiptScannerService.ScannedLineItem(existing.name(), amount, quantity, null));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static int quantityOrOne(ReceiptScannerService.ScannedLineItem item) {
        return item.quantity() != null ? item.quantity() : 1;
    }
}
