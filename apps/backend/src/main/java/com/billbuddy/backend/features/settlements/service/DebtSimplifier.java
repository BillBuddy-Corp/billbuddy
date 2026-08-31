package com.billbuddy.backend.features.settlements.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Greedy debt simplification: repeatedly matches the largest remaining creditor against the
// largest remaining debtor until every balance is zeroed out. Ties are broken by ascending user
// id so the result is deterministic for a given set of net balances.
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
    }

    private static final class Entry {
        final Long userId;
        BigDecimal remaining;

        Entry(Long userId, BigDecimal remaining) {
            this.userId = userId;
            this.remaining = remaining;
        }
    }

    public static List<Transfer> simplify(Map<Long, BigDecimal> netBalances) {
        List<Entry> creditors = new ArrayList<>();
        List<Entry> debtors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> e : netBalances.entrySet()) {
            int cmp = e.getValue().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new Entry(e.getKey(), e.getValue()));
            } else if (cmp < 0) {
                debtors.add(new Entry(e.getKey(), e.getValue().negate()));
            }
        }

        Comparator<Entry> byRemainingDescThenUserIdAsc =
                Comparator.<Entry, BigDecimal>comparing(en -> en.remaining).reversed()
                        .thenComparing(en -> en.userId);

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort(byRemainingDescThenUserIdAsc);
            debtors.sort(byRemainingDescThenUserIdAsc);

            Entry creditor = creditors.get(0);
            Entry debtor = debtors.get(0);
            BigDecimal amount = creditor.remaining.min(debtor.remaining);

            transfers.add(new Transfer(debtor.userId, creditor.userId, amount));

            creditor.remaining = creditor.remaining.subtract(amount);
            debtor.remaining = debtor.remaining.subtract(amount);

            if (creditor.remaining.compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(0);
            }
            if (debtor.remaining.compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(0);
            }
        }

        return transfers;
    }
}
