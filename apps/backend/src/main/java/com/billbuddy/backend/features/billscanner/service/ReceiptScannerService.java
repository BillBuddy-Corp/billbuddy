package com.billbuddy.backend.features.billscanner.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReceiptScannerService {

    // Throws ReceiptScanFailedException when the image genuinely isn't a readable receipt.
    // A partial read (some fields null) is not a failure -- only merchant/amount/etc missing
    // individually is expected and fine.
    ScannedReceipt scan(byte[] imageBytes, String mimeType);

    record ScannedReceipt(
            String merchant,
            BigDecimal amount,
            String currency,
            LocalDate transactionDate,
            BigDecimal otherDiscount,
            BigDecimal voucherAmount,
            List<ScannedLineItem> items
    ) {
    }

    record ScannedLineItem(String name, BigDecimal amount, Integer quantity, BigDecimal itemDiscount) {
    }
}
