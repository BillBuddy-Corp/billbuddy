package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseSplitCalculatorTest {

    // ===================== EQUAL SPLIT =====================

    @Test
    void equalSplit_dividesEvenly_whenTotalDividesCleanly() {
        Map<Long, BigDecimal> result = ExpenseSplitCalculator.equalSplit(
                new BigDecimal("90"), List.of(5L, 8L, 12L), 100L
        );

        assertThat(result.get(5L)).isEqualByComparingTo("30.00");
        assertThat(result.get(8L)).isEqualByComparingTo("30.00");
        assertThat(result.get(12L)).isEqualByComparingTo("30.00");
        assertThat(sum(result)).isEqualByComparingTo("90.00");
    }

    @Test
    void equalSplit_distributesRemainderCent_whenNotEvenlyDivisible() {
        // 1.00 / 3 = 0.333... -- classic case: someone has to absorb the leftover cent
        Map<Long, BigDecimal> result = ExpenseSplitCalculator.equalSplit(
                new BigDecimal("1.00"), List.of(5L, 8L, 12L), 101L // 101 % 3 = 2 -> sorted index 2 (id 12)
        );

        assertThat(result.get(5L)).isEqualByComparingTo("0.33");
        assertThat(result.get(8L)).isEqualByComparingTo("0.33");
        assertThat(result.get(12L)).isEqualByComparingTo("0.34");
        assertThat(sum(result)).isEqualByComparingTo("1.00");
    }

    @Test
    void equalSplit_rotatesWhoAbsorbsTheRemainder_basedOnRotationSeed() {
        List<Long> participants = List.of(5L, 8L, 12L);

        Map<Long, BigDecimal> seed101 = ExpenseSplitCalculator.equalSplit(new BigDecimal("1.00"), participants, 101L);
        Map<Long, BigDecimal> seed102 = ExpenseSplitCalculator.equalSplit(new BigDecimal("1.00"), participants, 102L);
        Map<Long, BigDecimal> seed103 = ExpenseSplitCalculator.equalSplit(new BigDecimal("1.00"), participants, 103L);

        // 101 % 3 = 2 -> id 12 gets the extra cent
        assertThat(seed101.get(12L)).isEqualByComparingTo("0.34");
        // 102 % 3 = 0 -> id 5 gets it
        assertThat(seed102.get(5L)).isEqualByComparingTo("0.34");
        // 103 % 3 = 1 -> id 8 gets it
        assertThat(seed103.get(8L)).isEqualByComparingTo("0.34");
    }

    @Test
    void equalSplit_sameExpenseAlwaysProducesSameSplit() {
        Map<Long, BigDecimal> first = ExpenseSplitCalculator.equalSplit(new BigDecimal("1.00"), List.of(5L, 8L, 12L), 42L);
        Map<Long, BigDecimal> second = ExpenseSplitCalculator.equalSplit(new BigDecimal("1.00"), List.of(5L, 8L, 12L), 42L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void equalSplit_throwsInvalidSplit_whenNoParticipants() {
        assertThatThrownBy(() -> ExpenseSplitCalculator.equalSplit(BigDecimal.TEN, List.of(), 1L))
                .isInstanceOf(InvalidSplitException.class);
    }

    // ===================== PERCENTAGE SPLIT =====================

    @Test
    void percentageSplit_computesSharesByPercentage() {
        Map<Long, BigDecimal> pct = new LinkedHashMap<>();
        pct.put(1L, new BigDecimal("50"));
        pct.put(2L, new BigDecimal("30"));
        pct.put(3L, new BigDecimal("20"));

        Map<Long, BigDecimal> result = ExpenseSplitCalculator.percentageSplit(new BigDecimal("100"), pct, 1L);

        assertThat(result.get(1L)).isEqualByComparingTo("50.00");
        assertThat(result.get(2L)).isEqualByComparingTo("30.00");
        assertThat(result.get(3L)).isEqualByComparingTo("20.00");
    }

    @Test
    void percentageSplit_throwsInvalidSplit_whenPercentagesDoNotSumTo100() {
        Map<Long, BigDecimal> pct = Map.of(1L, new BigDecimal("50"), 2L, new BigDecimal("40"));

        assertThatThrownBy(() -> ExpenseSplitCalculator.percentageSplit(new BigDecimal("100"), pct, 1L))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void percentageSplit_throwsInvalidSplit_whenEmpty() {
        assertThatThrownBy(() -> ExpenseSplitCalculator.percentageSplit(new BigDecimal("100"), Map.of(), 1L))
                .isInstanceOf(InvalidSplitException.class);
    }

    // ===================== EXACT SPLIT =====================

    @Test
    void exactSplit_returnsProvidedAmounts_whenSumMatchesTotal() {
        Map<Long, BigDecimal> amounts = Map.of(1L, new BigDecimal("60"), 2L, new BigDecimal("40"));

        Map<Long, BigDecimal> result = ExpenseSplitCalculator.exactSplit(new BigDecimal("100"), amounts);

        assertThat(result.get(1L)).isEqualByComparingTo("60.00");
        assertThat(result.get(2L)).isEqualByComparingTo("40.00");
    }

    @Test
    void exactSplit_throwsInvalidSplit_whenSumDoesNotMatchTotal() {
        Map<Long, BigDecimal> amounts = Map.of(1L, new BigDecimal("60"), 2L, new BigDecimal("30"));

        assertThatThrownBy(() -> ExpenseSplitCalculator.exactSplit(new BigDecimal("100"), amounts))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void exactSplit_throwsInvalidSplit_whenEmpty() {
        assertThatThrownBy(() -> ExpenseSplitCalculator.exactSplit(new BigDecimal("100"), Map.of()))
                .isInstanceOf(InvalidSplitException.class);
    }

    // ===================== ITEM SPLIT =====================

    @Test
    void itemSplit_computesSharesByRatio() {
        // 90 split 1:2 -> 30 / 60
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        shares.put(1L, BigDecimal.ONE);
        shares.put(2L, new BigDecimal("2"));

        Map<Long, BigDecimal> result = ExpenseSplitCalculator.itemSplit(new BigDecimal("90"), shares, 1L);

        assertThat(result.get(1L)).isEqualByComparingTo("30.00");
        assertThat(result.get(2L)).isEqualByComparingTo("60.00");
    }

    @Test
    void itemSplit_equalShares_distributesRemainderWithinTheItem() {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        shares.put(5L, BigDecimal.ONE);
        shares.put(8L, BigDecimal.ONE);
        shares.put(12L, BigDecimal.ONE);

        Map<Long, BigDecimal> result = ExpenseSplitCalculator.itemSplit(new BigDecimal("1.00"), shares, 101L);

        assertThat(sum(result)).isEqualByComparingTo("1.00");
    }

    @Test
    void itemSplit_throwsInvalidSplit_whenSharesSumToZero() {
        Map<Long, BigDecimal> shares = Map.of(1L, BigDecimal.ZERO);

        assertThatThrownBy(() -> ExpenseSplitCalculator.itemSplit(BigDecimal.TEN, shares, 1L))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void itemSplit_throwsInvalidSplit_whenEmpty() {
        assertThatThrownBy(() -> ExpenseSplitCalculator.itemSplit(BigDecimal.TEN, Map.of(), 1L))
                .isInstanceOf(InvalidSplitException.class);
    }

    // ===================== SUM VALIDATION =====================

    @Test
    void requireSumEqualsTotal_doesNotThrow_whenEqual() {
        ExpenseSplitCalculator.requireSumEqualsTotal(new BigDecimal("100"), new BigDecimal("100.00"), "Amounts");
    }

    @Test
    void requireSumEqualsTotal_throwsInvalidSplit_whenDifferent() {
        assertThatThrownBy(() ->
                ExpenseSplitCalculator.requireSumEqualsTotal(new BigDecimal("100"), new BigDecimal("99.99"), "Amounts")
        ).isInstanceOf(InvalidSplitException.class);
    }

    private BigDecimal sum(Map<Long, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
