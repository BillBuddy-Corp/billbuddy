package com.billbuddy.backend.features.settlements.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SettlementResponse {

    private Long id;
    private Long groupId;
    private Long paidByUserId;
    private String paidByName;
    private Long paidToUserId;
    private String paidToName;
    private BigDecimal amount;
    private String currency;
    private String note;
    private Long createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
