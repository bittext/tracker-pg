package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhDailySnapshotHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Current holdings with qty / price / market-value deltas vs {@code prior}. When {@code prior} is
     * null, change fields stay empty. Holdings that left since the prior capture are appended as
     * {@code exited} rows with zero current qty/value.
     */
    static List<RobinhoodRhDailySnapshotHoldingDto> holdingsWithPriorDeltas(
            List<RobinhoodRhHoldingDto> current, List<RobinhoodRhHoldingDto> prior) {
        List<RobinhoodRhHoldingDto> currentList = current == null ? List.of() : current;
        if (prior == null) {
            List<RobinhoodRhDailySnapshotHoldingDto> out = new ArrayList<>(currentList.size());
            for (RobinhoodRhHoldingDto holding : currentList) {
                out.add(new RobinhoodRhDailySnapshotHoldingDto(holding, null, null, null, false));
            }
            return out;
        }
        Map<String, RobinhoodRhHoldingDto> priorByKey = holdingsByPositionKey(prior);
        Set<String> seen = new HashSet<>();
        List<RobinhoodRhDailySnapshotHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto holding : currentList) {
            String key = holdingPositionKey(holding);
            RobinhoodRhHoldingDto previous = key.isEmpty() ? null : priorByKey.get(key);
            if (!key.isEmpty()) {
                seen.add(key);
            }
            out.add(deltaRow(holding, previous, false));
        }
        for (RobinhoodRhHoldingDto previous : prior) {
            String key = holdingPositionKey(previous);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.add(deltaRow(asExited(previous), previous, true));
        }
        return out;
    }

    static BigDecimal signedMoneyDelta(BigDecimal current, BigDecimal prior) {
        return signedDelta(current, prior, 2);
    }

    static BigDecimal signedDelta(BigDecimal current, BigDecimal prior, int scale) {
        BigDecimal delta = nullToZero(current).subtract(nullToZero(prior)).setScale(scale, RoundingMode.HALF_UP);
        return delta.signum() == 0 ? null : delta;
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

    private static Map<String, RobinhoodRhHoldingDto> holdingsByPositionKey(List<RobinhoodRhHoldingDto> holdings) {
        Map<String, RobinhoodRhHoldingDto> out = new TreeMap<>();
        if (holdings == null) {
            return out;
        }
        for (RobinhoodRhHoldingDto holding : holdings) {
            String key = holdingPositionKey(holding);
            if (key.isEmpty()) {
                continue;
            }
            out.merge(key, holding, RobinhoodRhDailySnapshotCompare::combineHoldings);
        }
        return out;
    }

    private static RobinhoodRhHoldingDto combineHoldings(RobinhoodRhHoldingDto left, RobinhoodRhHoldingDto right) {
        return new RobinhoodRhHoldingDto(
                left.symbol(),
                left.positionType(),
                nullToZero(left.quantity()).add(nullToZero(right.quantity())),
                left.averageBuyPrice(),
                left.currentUnitPrice(),
                nullToZero(left.marketValue()).add(nullToZero(right.marketValue())),
                nullToZero(left.costBasis()).add(nullToZero(right.costBasis())),
                nullToZero(left.unrealizedPnL()).add(nullToZero(right.unrealizedPnL())),
                left.unrealizedPnLPercent(),
                left.positionKey(),
                left.chainSymbol(),
                left.optionType(),
                left.strikePrice(),
                left.expirationDate());
    }

    private static RobinhoodRhDailySnapshotHoldingDto deltaRow(
            RobinhoodRhHoldingDto current, RobinhoodRhHoldingDto prior, boolean exited) {
        BigDecimal priorQty = prior == null ? BigDecimal.ZERO : prior.quantity();
        BigDecimal priorValue = prior == null ? BigDecimal.ZERO : prior.marketValue();
        BigDecimal priceChange = prior == null || exited
                ? null
                : signedDelta(current.currentUnitPrice(), prior.currentUnitPrice(), 4);
        return new RobinhoodRhDailySnapshotHoldingDto(
                current,
                signedDelta(current.quantity(), priorQty, 4),
                priceChange,
                signedMoneyDelta(current.marketValue(), priorValue),
                exited);
    }

    private static RobinhoodRhHoldingDto asExited(RobinhoodRhHoldingDto prior) {
        return new RobinhoodRhHoldingDto(
                prior.symbol(),
                prior.positionType(),
                BigDecimal.ZERO,
                prior.averageBuyPrice(),
                prior.currentUnitPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                prior.positionKey(),
                prior.chainSymbol(),
                prior.optionType(),
                prior.strikePrice(),
                prior.expirationDate());
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
