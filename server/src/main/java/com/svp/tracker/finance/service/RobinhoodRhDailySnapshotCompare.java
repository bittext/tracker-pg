package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Shared snapshot comparison for Daily Tracker report UI and spike alerts. */
final class RobinhoodRhDailySnapshotCompare {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private RobinhoodRhDailySnapshotCompare() {}

    static Optional<RobinhoodRhDailySnapshot> findPriorSnapshot(
            RobinhoodRhDailySnapshotRepository repository,
            long ownerUserId,
            String suffix,
            Instant before) {
        if (repository == null || suffix == null || suffix.isBlank() || before == null) {
            return Optional.empty();
        }
        return repository.findTopByOwnerUserIdAndAccountSuffixAndSnapshotAtLessThanOrderBySnapshotAtDesc(
                ownerUserId, suffix.trim(), before);
    }

    static Optional<RobinhoodRhDailySnapshot> findPriorSnapshotInMemory(
            List<RobinhoodRhDailySnapshot> allRows, long ownerUserId, String suffix, Instant before) {
        if (allRows == null || suffix == null || suffix.isBlank() || before == null) {
            return Optional.empty();
        }
        return allRows.stream()
                .filter(r -> r.getOwnerUserId() == ownerUserId)
                .filter(r -> suffix.equals(r.getAccountSuffix()))
                .filter(r -> r.getSnapshotAt() != null && r.getSnapshotAt().isBefore(before))
                .max(Comparator.comparing(RobinhoodRhDailySnapshot::getSnapshotAt));
    }

    static BigDecimal deltaDollars(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        BigDecimal priorTotal = nullToZero(prior == null ? null : prior.getTotalAccountValue());
        BigDecimal currentTotal = nullToZero(current == null ? null : current.getTotalAccountValue());
        return currentTotal.subtract(priorTotal).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal absDeltaDollars(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        return deltaDollars(prior, current).abs();
    }

    /** Percent change vs prior total; empty when prior total is zero or negative. */
    static Optional<BigDecimal> deltaPercentAbs(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        BigDecimal priorTotal = nullToZero(prior == null ? null : prior.getTotalAccountValue());
        if (priorTotal.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal pct = absDeltaDollars(prior, current)
                .multiply(BigDecimal.valueOf(100))
                .divide(priorTotal, 4, RoundingMode.HALF_UP);
        return Optional.of(pct);
    }

    static boolean positionsChanged(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        return !holdingsQuantityByPositionKey(prior).equals(holdingsQuantityByPositionKey(current));
    }

    static Map<String, BigDecimal> holdingsQuantityByPositionKey(RobinhoodRhDailySnapshot row) {
        if (row == null || row.getHoldingsJson() == null || row.getHoldingsJson().isBlank()) {
            return Map.of();
        }
        List<RobinhoodRhHoldingDto> holdings = readJson(row.getHoldingsJson(), new TypeReference<>() {});
        if (holdings == null || holdings.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> quantities = new TreeMap<>();
        for (RobinhoodRhHoldingDto holding : holdings) {
            String key = holdingPositionKey(holding);
            if (key.isEmpty()) {
                continue;
            }
            BigDecimal qty = nullToZero(holding.quantity()).setScale(4, RoundingMode.HALF_UP);
            if (qty.signum() == 0) {
                continue;
            }
            quantities.merge(key, qty, BigDecimal::add);
        }
        return quantities;
    }

    private static String holdingPositionKey(RobinhoodRhHoldingDto holding) {
        if (holding == null || holding.symbol() == null || holding.symbol().isBlank()) {
            return "";
        }
        String symbol = holding.symbol().trim().toUpperCase(Locale.ROOT);
        String type = holding.positionType() == null || holding.positionType().isBlank()
                ? "STOCK"
                : holding.positionType().trim().toUpperCase(Locale.ROOT);
        if ("OPTION".equals(type)) {
            String contract = RobinhoodRhContractKeys.contractKeyForHolding(holding);
            if (contract != null && !contract.isBlank()) {
                return contract;
            }
        }
        return symbol + "|" + type;
    }

    private static <T> T readJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
