package com.billbuddy.backend.features.billscanner.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ReceiptScanItemResponse {

    private String name;
    private BigDecimal amount;
    private Integer quantity;
}
