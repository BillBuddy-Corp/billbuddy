package com.billbuddy.backend.features.settlements.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimplifierTest {

    @Test
    void simplify_returnsEmpty_whenAllBalancesAreZero() {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, BigDecimal.ZERO);
        balances.put(2L, BigDecimal.ZERO);

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        assertThat(result).isEmpty();
    }

    @Test
    void simplify_returnsEmpty_whenBalancesMapIsEmpty() {
        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void simplify_producesOneTransfer_forSimpleTwoPersonDebt() {
        Map<Long, BigDecimal> balances = Map.of(
                1L, new BigDecimal("50.00"),
                2L, new BigDecimal("-50.00")
        );

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        assertThat(result).containsExactly(new DebtSimplifier.Transfer(2L, 1L, new BigDecimal("50.00")));
    }

    @Test
    void simplify_collapsesAChain_intoOneDirectTransfer() {
        // A owes B 10, B owes C 10 -- nets to A -10, B 0, C +10. A single A->C transfer settles it,
        // rather than two hops through B.
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("-10.00")); // A
        balances.put(2L, BigDecimal.ZERO);           // B
        balances.put(3L, new BigDecimal("10.00"));   // C

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        assertThat(result).containsExactly(new DebtSimplifier.Transfer(1L, 3L, new BigDecimal("10.00")));
    }

    @Test
    void simplify_matchesLargestCreditorAgainstLargestDebtorFirst() {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("70.00"));  // biggest creditor
        balances.put(2L, new BigDecimal("30.00"));  // smaller creditor
        balances.put(3L, new BigDecimal("-80.00")); // biggest debtor
        balances.put(4L, new BigDecimal("-20.00")); // smaller debtor

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        // debtor 3 (largest) pays creditor 1 (largest) first: min(80, 70) = 70
        assertThat(result.get(0)).isEqualTo(new DebtSimplifier.Transfer(3L, 1L, new BigDecimal("70.00")));

        BigDecimal totalTransferred = result.stream().map(DebtSimplifier.Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo("100.00");
    }

    @Test
    void simplify_breaksTiesByAscendingUserId_forDeterminism() {
        // two creditors tied at 50, two debtors tied at -50 -- lower user id goes first on both sides
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(20L, new BigDecimal("50.00"));
        balances.put(10L, new BigDecimal("50.00"));
        balances.put(40L, new BigDecimal("-50.00"));
        balances.put(30L, new BigDecimal("-50.00"));

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        assertThat(result).containsExactly(
                new DebtSimplifier.Transfer(30L, 10L, new BigDecimal("50.00")),
                new DebtSimplifier.Transfer(40L, 20L, new BigDecimal("50.00"))
        );
    }

    @Test
    void simplify_isDeterministic_forTheSameInput() {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("120.00"));
        balances.put(2L, new BigDecimal("-40.00"));
        balances.put(3L, new BigDecimal("-80.00"));

        List<DebtSimplifier.Transfer> first = DebtSimplifier.simplify(balances);
        List<DebtSimplifier.Transfer> second = DebtSimplifier.simplify(balances);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void simplify_everyCreditorsShareSumsToTheirNetBalance() {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal("60.00"));
        balances.put(2L, new BigDecimal("40.00"));
        balances.put(3L, new BigDecimal("-25.00"));
        balances.put(4L, new BigDecimal("-35.00"));
        balances.put(5L, new BigDecimal("-40.00"));

        List<DebtSimplifier.Transfer> result = DebtSimplifier.simplify(balances);

        BigDecimal toUser1 = sumTo(result, 1L);
        BigDecimal toUser2 = sumTo(result, 2L);
        assertThat(toUser1).isEqualByComparingTo("60.00");
        assertThat(toUser2).isEqualByComparingTo("40.00");
    }

    private BigDecimal sumTo(List<DebtSimplifier.Transfer> transfers, Long userId) {
        return transfers.stream()
                .filter(t -> t.toUserId().equals(userId))
                .map(DebtSimplifier.Transfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
