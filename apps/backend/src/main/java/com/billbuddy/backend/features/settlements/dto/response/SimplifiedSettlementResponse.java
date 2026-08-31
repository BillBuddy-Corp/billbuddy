package com.billbuddy.backend.features.settlements.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SimplifiedSettlementResponse {

    private Long fromUserId;
    private String fromName;
    private Long toUserId;
    private String toName;
    private BigDecimal amount;
}
