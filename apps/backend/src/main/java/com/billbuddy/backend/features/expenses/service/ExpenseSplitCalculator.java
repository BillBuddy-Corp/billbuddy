package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.InvalidSplitException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpenseSplitCalculator {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private ExpenseSplitCalculator() {
        // prevent instantiation
    }

    // Remainder cents from rounding start at (rotationSeed % participantCount), sorted by id, so rounding dust rotates instead of always landing on the same person.
    public static Map<Long, BigDecimal> equalSplit(BigDecimal total, List<Long> participantUserIds, long rotationSeed) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new InvalidSplitException("At least one participant is required for an equal split");
        }
        List<Long> sortedIds = participantUserIds.stream().distinct().sorted().toList();

        BigDecimal rawShare = total.divide(BigDecimal.valueOf(sortedIds.size()), 10, RoundingMode.HALF_UP);
        LinkedHashMap<Long, BigDecimal> rawShares = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            rawShares.put(id, rawShare);
        }
        return floorAndDistributeRemainder(total, rawShares, rotationSeed);
    }

    // percentagesByUser values must sum to exactly 100.
    public static Map<Long, BigDecimal> percentageSplit(BigDecimal total, Map<Long, BigDecimal> percentagesByUser, long rotationSeed) {
        if (percentagesByUser == null || percentagesByUser.isEmpty()) {
            throw new InvalidSplitException("At least one percentage entry is required for a percentage split");
        }
        BigDecimal sumPct = percentagesByUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumPct.compareTo(ONE_HUNDRED) != 0) {
            throw new InvalidSplitException("Percentages must sum to exactly 100, got " + sumPct);
        }

        List<Long> sortedIds = percentagesByUser.keySet().stream().sorted().toList();
        LinkedHashMap<Long, BigDecimal> rawShares = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            BigDecimal pct = percentagesByUser.get(id);
            rawShares.put(id, total.multiply(pct).divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
        }
        return floorAndDistributeRemainder(total, rawShares, rotationSeed);
    }

    // amountsByUser must already sum to exactly total, validated below before use.
    public static Map<Long, BigDecimal> exactSplit(BigDecimal total, Map<Long, BigDecimal> amountsByUser) {
        if (amountsByUser == null || amountsByUser.isEmpty()) {
            throw new InvalidSplitException("At least one exact amount entry is required");
        }
        requireSumEqualsTotal(total, sumOf(amountsByUser.values()), "Exact amounts");

        LinkedHashMap<Long, BigDecimal> result = new LinkedHashMap<>();
        amountsByUser.forEach((id, amount) -> result.put(id, amount.setScale(2, RoundingMode.HALF_UP)));
        return result;
    }

    // Splits one item's amount by share ratio, using the same rotation as equalSplit but keyed off the item's own id.
    public static Map<Long, BigDecimal> itemSplit(BigDecimal itemAmount, Map<Long, BigDecimal> sharesByUser, long rotationSeed) {
        if (sharesByUser == null || sharesByUser.isEmpty()) {
            throw new InvalidSplitException("Each item needs at least one assignment");
        }
        BigDecimal totalShares = sharesByUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalShares.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSplitException("Item shares must sum to more than zero");
        }

        List<Long> sortedIds = sharesByUser.keySet().stream().sorted().toList();
        LinkedHashMap<Long, BigDecimal> rawShares = new LinkedHashMap<>();
        for (Long id : sortedIds) {
            BigDecimal share = sharesByUser.get(id);
            rawShares.put(id, itemAmount.multiply(share).divide(totalShares, 10, RoundingMode.HALF_UP));
        }
        return floorAndDistributeRemainder(itemAmount, rawShares, rotationSeed);
    }

    public static void requireSumEqualsTotal(BigDecimal total, BigDecimal sum, String context) {
        if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(total.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new InvalidSplitException(context + " must sum to the expense total (" + total + "), got " + sum);
        }
    }

    public static BigDecimal sumOf(java.util.Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<Long, BigDecimal> floorAndDistributeRemainder(
            BigDecimal total, LinkedHashMap<Long, BigDecimal> rawShares, long rotationSeed
    ) {
        List<Long> orderedIds = new ArrayList<>(rawShares.keySet());
        LinkedHashMap<Long, BigDecimal> floors = new LinkedHashMap<>();
        BigDecimal sumFloors = BigDecimal.ZERO;
        for (Long id : orderedIds) {
            BigDecimal floor = rawShares.get(id).setScale(2, RoundingMode.FLOOR);
            floors.put(id, floor);
            sumFloors = sumFloors.add(floor);
        }

        BigDecimal remainder = total.setScale(2, RoundingMode.HALF_UP).subtract(sumFloors);
        int remainderCents = remainder.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();

        int n = orderedIds.size();
        if (remainderCents < 0 || remainderCents >= n) {
            throw new InvalidSplitException("Unable to reconcile split amounts for the given total");
        }

        int startIndex = Math.floorMod(rotationSeed, n);
        for (int i = 0; i < remainderCents; i++) {
            Long id = orderedIds.get((startIndex + i) % n);
            floors.put(id, floors.get(id).add(ONE_CENT));
        }
        return floors;
    }
}
