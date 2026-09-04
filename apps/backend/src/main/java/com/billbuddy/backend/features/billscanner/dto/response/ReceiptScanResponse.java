package com.billbuddy.backend.features.billscanner.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReceiptScanResponse {

    private Long fileId;
    private String merchant;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private BigDecimal otherDiscount;
    private BigDecimal voucherAmount;
    private BigDecimal subtotal;
    private boolean needsReview;
    private boolean discountsNeedReview;
    private List<ReceiptScanItemResponse> items;
}
