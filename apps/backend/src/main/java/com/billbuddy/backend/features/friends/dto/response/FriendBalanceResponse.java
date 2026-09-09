package com.billbuddy.backend.features.friends.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

// One entry per currency the two friends share activity in -- balances across different shared
// groups (or non-group expenses) can legitimately be in different currencies, so this is never
// collapsed into a single converted number.
@Getter
@AllArgsConstructor
public class FriendBalanceResponse {

    private String currency;

    // positive: the friend owes the caller. negative: the caller owes the friend.
    private BigDecimal amount;
}
