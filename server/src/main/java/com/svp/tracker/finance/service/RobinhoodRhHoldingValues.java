package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Derives per-position market value, cost basis, and unrealized P&amp;L for RH holdings displays. */
final class RobinhoodRhHoldingValues {

    private static final BigDecimal OPTION_INFLATION_RATIO_LOW = BigDecimal.valueOf(75);
    private static final BigDecimal OPTION_INFLATION_RATIO_HIGH = BigDecimal.valueOf(125);
    /** Robinhood MCP reports option average_price as per-contract premium; app displays per-share. */
    private static final BigDecimal OPTION_CONTRACT_MULTIPLIER = BigDecimal.valueOf(100);

    private RobinhoodRhHoldingValues() {}

    static List<RobinhoodRhHoldingDto> fromPositions(
            List<RobinhoodAgenticPosition> positions,
            BigDecimal accountEquityMarketValue,
            RobinhoodRhLiveQuotesDto liveQuotes) {
        List<RobinhoodRhHoldingDto> raw = new ArrayList<>();
        for (RobinhoodAgenticPosition p : positions) {
            BigDecimal qty = nullToZero(p.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal avg = nullToZero(p.getAverageBuyPrice());
            if (isOptionType(p.getPositionType())) {
                avg = normalizeOptionAveragePerShare(avg);
            }
            raw.add(new RobinhoodRhHoldingDto(
                    p.getSymbol(),
                    p.getPositionType(),
                    qty,
                    scaleUnitPrice(avg),
                    BigDecimal.ZERO,
                    scaleOptionMarketValue(isOptionType(p.getPositionType()), decimalOrZero(p.getMarketValue())),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));
        }
        raw.sort(Comparator.comparing(RobinhoodRhHoldingDto::symbol, String.CASE_INSENSITIVE_ORDER));
        Map<String, String> optionInstrumentIds = RobinhoodRhHoldingQuoteService.instrumentIdsByMatchKey(positions);
        return finalizeHoldings(raw, accountEquityMarketValue, liveQuotes, optionInstrumentIds);
    }

    static List<RobinhoodRhHoldingDto> finalizeHoldings(
            List<RobinhoodRhHoldingDto> holdings,
            BigDecimal accountEquityMarketValue,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhHoldingDto> working = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            working.add(restoreMarketValueIfMissing(
                    normalizeOptionTotalMarketValue(clearComputedFields(normalizeOptionAverage(h)))));
        }

        Map<String, String> optionIds =
                optionInstrumentByMatchKey == null ? Map.of() : optionInstrumentByMatchKey;
        RobinhoodRhLiveQuotesDto live = liveQuotes == null ? RobinhoodRhLiveQuotesDto.empty() : liveQuotes;

        for (int i = 0; i < working.size(); i++) {
            working.set(i, applyLiveMarketValue(working.get(i), live, optionIds));
        }

        allocateStockEquityWhenNeeded(working, accountEquityMarketValue);

        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : working) {
            out.add(buildHolding(h, live, optionIds));
        }
        return out;
    }

    /**
     * Fix legacy option rows deserialized from snapshot JSON without re-fetching live quotes.
     * Stored rows may have contract-premium average_price and an inflated currentUnitPrice.
     */
    static List<RobinhoodRhHoldingDto> normalizeStoredSnapshotHoldings(List<RobinhoodRhHoldingDto> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            out.add(normalizeStoredSnapshotHolding(h));
        }
        return out;
    }

    static RobinhoodRhHoldingDto normalizeStoredSnapshotHolding(RobinhoodRhHoldingDto h) {
        if (!isOptionLike(h)) {
            return h;
        }
        RobinhoodRhHoldingDto working = withAverage(h, normalizeOptionAveragePerShare(h.averageBuyPrice()));
        if (!isOptionType(working.positionType())) {
            working = new RobinhoodRhHoldingDto(
                    working.symbol(),
                    "option",
                    working.quantity(),
                    working.averageBuyPrice(),
                    working.currentUnitPrice(),
                    working.marketValue(),
                    working.costBasis(),
                    working.unrealizedPnL(),
                    working.unrealizedPnLPercent());
        }
        working = normalizeOptionTotalMarketValue(working);
        BigDecimal qty = nullToZero(working.quantity());
        BigDecimal avg = scaleUnitPrice(nullToZero(working.averageBuyPrice()));
        BigDecimal cost = deriveCostBasis(withAverage(working, avg));
        BigDecimal mv = deflateInflatedOptionMarketValue(working, scaleMoney(effectiveMarketValue(working)));
        BigDecimal storedUnrealized = nullToZero(working.unrealizedPnL());
        if (storedUnrealized.compareTo(BigDecimal.ZERO) != 0
                && cost.compareTo(BigDecimal.ZERO) > 0
                && mv.subtract(cost).abs().compareTo(new BigDecimal("0.05")) <= 0) {
            mv = cost.add(storedUnrealized).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal perShareFromMv = optionPerShareFromMarketValue(mv, qty, avg);
        BigDecimal perShareFromCurrent = normalizeOptionPerShareFromLegacy(working.currentUnitPrice(), avg);
        BigDecimal perShare = resolveOptionSnapshotPerShare(avg, perShareFromMv, perShareFromCurrent);
        BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        return new RobinhoodRhHoldingDto(
                working.symbol(),
                working.positionType(),
                qty,
                avg,
                scaleUnitPrice(perShare),
                scaleOptionMarketValue(isOption(working), mv),
                scaleMoney(cost),
                scaleMoney(unrealized),
                unrealizedPnLPercent(unrealized, cost));
    }

    private static boolean isOptionLike(RobinhoodRhHoldingDto h) {
        if (isOptionType(h.positionType())) {
            return true;
        }
        BigDecimal qty = nullToZero(h.quantity()).abs();
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        return qty.compareTo(BigDecimal.ZERO) > 0
                && qty.compareTo(BigDecimal.valueOf(20)) <= 0
                && avg.compareTo(BigDecimal.valueOf(500)) > 0
                && avg.compareTo(BigDecimal.valueOf(100000)) < 0;
    }

    private static BigDecimal deflateInflatedOptionMarketValue(RobinhoodRhHoldingDto h, BigDecimal mv) {
        if (mv.compareTo(BigDecimal.ZERO) <= 0) {
            return mv;
        }
        BigDecimal qty = nullToZero(h.quantity()).abs();
        BigDecimal avg = normalizeOptionAveragePerShare(h.averageBuyPrice());
        if (qty.compareTo(BigDecimal.ZERO) <= 0 || avg.compareTo(BigDecimal.ZERO) <= 0) {
            return mv;
        }
        BigDecimal perShare = mv.divide(qty.multiply(OPTION_CONTRACT_MULTIPLIER), 4, RoundingMode.HALF_UP);
        if (perShare.compareTo(avg.multiply(BigDecimal.valueOf(25))) > 0) {
            return mv.divide(OPTION_CONTRACT_MULTIPLIER, 2, RoundingMode.HALF_UP);
        }
        return mv;
    }

    private static BigDecimal optionPerShareFromMarketValue(BigDecimal mv, BigDecimal qty, BigDecimal avgPerShare) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0 || mv.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal perShare = mv.divide(qty.abs().multiply(OPTION_CONTRACT_MULTIPLIER), 4, RoundingMode.HALF_UP);
        if (avgPerShare.compareTo(BigDecimal.ZERO) > 0
                && perShare.compareTo(avgPerShare.multiply(BigDecimal.valueOf(25))) > 0) {
            perShare = perShare.divide(OPTION_CONTRACT_MULTIPLIER, 4, RoundingMode.HALF_UP);
        }
        return perShare;
    }

    private static BigDecimal resolveOptionSnapshotPerShare(
            BigDecimal avgPerShare, BigDecimal perShareFromMv, BigDecimal perShareFromCurrent) {
        boolean mvValid = perShareFromMv.compareTo(BigDecimal.ZERO) > 0;
        boolean currentValid = perShareFromCurrent.compareTo(BigDecimal.ZERO) > 0;
        if (mvValid && currentValid && avgPerShare.compareTo(BigDecimal.ZERO) > 0) {
            boolean mvLooksLikeCost =
                    perShareFromMv.subtract(avgPerShare).abs().compareTo(new BigDecimal("0.05")) <= 0;
            if (mvLooksLikeCost && perShareFromCurrent.compareTo(avgPerShare) > 0) {
                return perShareFromCurrent;
            }
            if (perShareFromMv.compareTo(avgPerShare.multiply(BigDecimal.valueOf(50))) > 0) {
                return perShareFromCurrent;
            }
            return perShareFromMv;
        }
        if (mvValid) {
            return perShareFromMv;
        }
        return perShareFromCurrent;
    }

    /** Allocate stock-only portfolio equity to equity rows that still lack a market value. */
    private static void allocateStockEquityWhenNeeded(
            List<RobinhoodRhHoldingDto> holdings, BigDecimal accountEquityMarketValue) {
        BigDecimal stockPool = nullToZero(accountEquityMarketValue);
        if (stockPool.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal equityMvKnown = BigDecimal.ZERO;
        List<Integer> equityNeeding = new ArrayList<>();
        for (int i = 0; i < holdings.size(); i++) {
            RobinhoodRhHoldingDto h = holdings.get(i);
            if (!"equity".equalsIgnoreCase(h.positionType())) {
                continue;
            }
            if (marketValueMissing(h)) {
                equityNeeding.add(i);
            } else {
                equityMvKnown = equityMvKnown.add(nullToZero(h.marketValue()));
            }
        }

        BigDecimal remaining = stockPool.subtract(equityMvKnown);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || equityNeeding.isEmpty()) {
            return;
        }
        // Do not spread a portfolio total that is smaller than known equity MV — that would shrink live quotes.
        if (stockPool.compareTo(equityMvKnown) < 0) {
            return;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        for (int i : equityNeeding) {
            RobinhoodRhHoldingDto h = holdings.get(i);
            totalCost = totalCost.add(deriveCostBasis(h));
        }

        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int j = 0; j < equityNeeding.size(); j++) {
                int i = equityNeeding.get(j);
                RobinhoodRhHoldingDto h = holdings.get(i);
                BigDecimal cost = deriveCostBasis(h);
                BigDecimal mv;
                if (j == equityNeeding.size() - 1) {
                    mv = remaining.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
                } else {
                    mv = remaining.multiply(cost).divide(totalCost, 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(mv);
                }
                holdings.set(i, withMarketValue(h, mv));
            }
            return;
        }
        if (equityNeeding.size() == 1) {
            holdings.set(equityNeeding.get(0), withMarketValue(holdings.get(equityNeeding.get(0)), remaining));
        }
    }

    private static RobinhoodRhHoldingDto applyLiveMarketValue(
            RobinhoodRhHoldingDto h,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return h;
        }
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
            BigDecimal current = equityCurrentUnitPrice(h.symbol(), liveQuotes);
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                return withMarketValue(h, marketValueFromCurrent(h, qty, current));
            }
            return h;
        }
        if (isOption(h)) {
            String instrumentId = RobinhoodRhHoldingQuoteService.lookupOptionInstrumentId(h, optionInstrumentByMatchKey);
            if (instrumentId == null) {
                return h;
            }
            BigDecimal markPerShare = normalizeOptionMarkPerShare(
                    liveQuotes.optionMarkPerShareByInstrumentId().get(instrumentId));
            if (markPerShare.compareTo(BigDecimal.ZERO) <= 0) {
                return h;
            }
            return withMarketValue(h, marketValueFromCurrent(h, qty, markPerShare));
        }
        return h;
    }

    private static BigDecimal equityCurrentUnitPrice(String symbol, RobinhoodRhLiveQuotesDto liveQuotes) {
        if (symbol == null || symbol.isBlank()) {
            return BigDecimal.ZERO;
        }
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        BigDecimal rh = liveQuotes.equityPriceBySymbol().get(key);
        if (rh != null && rh.compareTo(BigDecimal.ZERO) > 0) {
            return rh;
        }
        return BigDecimal.ZERO;
    }

    private static RobinhoodRhHoldingDto normalizeOptionAverage(RobinhoodRhHoldingDto h) {
        if (!isOption(h)) {
            return h;
        }
        return withAverage(h, normalizeOptionAveragePerShare(h.averageBuyPrice()));
    }

    /**
     * Robinhood MCP average_price is per-contract premium (e.g. 1000 = $10.00/share).
     * Normalize to per-share for display and quote math.
     */
    private static BigDecimal normalizeOptionAveragePerShare(BigDecimal raw) {
        if (raw == null || raw.compareTo(BigDecimal.ZERO) == 0) {
            return raw == null ? BigDecimal.ZERO : raw;
        }
        if (raw.compareTo(BigDecimal.valueOf(100)) > 0) {
            return raw.divide(OPTION_CONTRACT_MULTIPLIER, 4, RoundingMode.HALF_UP);
        }
        return raw;
    }

    private static BigDecimal normalizeOptionMarkPerShare(BigDecimal markPerShare) {
        if (markPerShare == null || markPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (markPerShare.compareTo(BigDecimal.valueOf(100)) > 0) {
            return markPerShare.divide(OPTION_CONTRACT_MULTIPLIER, 2, RoundingMode.HALF_UP);
        }
        return markPerShare.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeOptionPerShareFromLegacy(BigDecimal raw, BigDecimal normalizedAvg) {
        BigDecimal v = normalizeOptionAveragePerShare(raw);
        if (v.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (normalizedAvg.compareTo(BigDecimal.ZERO) > 0
                && v.compareTo(normalizedAvg.multiply(BigDecimal.valueOf(25))) > 0) {
            v = v.divide(OPTION_CONTRACT_MULTIPLIER, 4, RoundingMode.HALF_UP);
        }
        return v;
    }

    private static RobinhoodRhHoldingDto normalizeOptionTotalMarketValue(RobinhoodRhHoldingDto h) {
        if (!isOption(h)) {
            return h;
        }
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal mv = nullToZero(h.marketValue());

        if (cost.compareTo(BigDecimal.ZERO) > 0 && mv.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = mv.divide(cost, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(OPTION_INFLATION_RATIO_LOW) >= 0
                    && ratio.compareTo(OPTION_INFLATION_RATIO_HIGH) <= 0) {
                return withMarketValue(h, mv.divide(OPTION_CONTRACT_MULTIPLIER, 2, RoundingMode.HALF_UP));
            }
        }
        return h;
    }

    private static RobinhoodRhHoldingDto buildHolding(
            RobinhoodRhHoldingDto h,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = scaleUnitPrice(nullToZero(h.averageBuyPrice()));
        BigDecimal cost = deriveCostBasis(withAverage(h, avg));
        BigDecimal current = resolveCurrentUnitPrice(h, liveQuotes, optionInstrumentByMatchKey);
        BigDecimal mv = marketValueFromCurrent(h, qty, current);
        if (mv.compareTo(BigDecimal.ZERO) == 0) {
            mv = effectiveMarketValue(h);
            current = resolveCurrentUnitPrice(withMarketValue(h, mv), liveQuotes, optionInstrumentByMatchKey);
            mv = marketValueFromCurrent(h, qty, current);
        }
        BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = unrealizedPnLPercent(unrealized, cost);
        BigDecimal currentUnit;
        if (isOption(h)) {
            currentUnit = scaleUnitPrice(current);
        } else if (qty.compareTo(BigDecimal.ZERO) > 0 && mv.compareTo(BigDecimal.ZERO) > 0) {
            currentUnit = mv.divide(qty.abs(), 4, RoundingMode.HALF_UP);
        } else {
            currentUnit = scaleUnitPrice(current);
        }
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                qty,
                avg,
                currentUnit,
                scaleMoney(mv),
                scaleMoney(cost),
                scaleMoney(unrealized),
                pnlPercent);
    }

    private static RobinhoodRhHoldingDto withAverage(RobinhoodRhHoldingDto h, BigDecimal avg) {
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                h.quantity(),
                avg,
                h.currentUnitPrice(),
                h.marketValue(),
                h.costBasis(),
                h.unrealizedPnL(),
                h.unrealizedPnLPercent());
    }

    private static BigDecimal resolveCurrentUnitPrice(
            RobinhoodRhHoldingDto h,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
            BigDecimal current = equityCurrentUnitPrice(h.symbol(), liveQuotes);
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                return current;
            }
        }
        if (isOption(h)) {
            String instrumentId = RobinhoodRhHoldingQuoteService.lookupOptionInstrumentId(h, optionInstrumentByMatchKey);
            if (instrumentId != null) {
                BigDecimal markPerShare = liveQuotes.optionMarkPerShareByInstrumentId().get(instrumentId);
                if (markPerShare != null && markPerShare.compareTo(BigDecimal.ZERO) > 0) {
                    return normalizeOptionMarkPerShare(markPerShare);
                }
            }
        }
        BigDecimal mv = effectiveMarketValue(h);
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return perShareFromTotal(h, mv, qty);
        }
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal unrealized = h.unrealizedPnL();
        if (unrealized != null
                && unrealized.compareTo(BigDecimal.ZERO) != 0
                && qty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal implied = cost.add(unrealized);
            if (implied.compareTo(BigDecimal.ZERO) > 0) {
                return perShareFromTotal(h, implied, qty);
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal perShareFromTotal(RobinhoodRhHoldingDto h, BigDecimal total, BigDecimal qty) {
        BigDecimal perUnit = total.divide(qty.abs(), 4, RoundingMode.HALF_UP);
        if (isOption(h)) {
            return perUnit.divide(OPTION_CONTRACT_MULTIPLIER, 4, RoundingMode.HALF_UP);
        }
        return perUnit;
    }

    private static RobinhoodRhHoldingDto clearComputedFields(RobinhoodRhHoldingDto h) {
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                h.quantity(),
                h.averageBuyPrice(),
                BigDecimal.ZERO,
                h.marketValue(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static BigDecimal deriveCostBasis(RobinhoodRhHoldingDto h) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        if (qty.compareTo(BigDecimal.ZERO) > 0 && avg.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cost = qty.abs().multiply(avg);
            if (isOption(h)) {
                cost = cost.multiply(OPTION_CONTRACT_MULTIPLIER);
            }
            return cost.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal stored = nullToZero(h.costBasis());
        if (stored.compareTo(BigDecimal.ZERO) > 0) {
            return stored.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal marketValueFromCurrent(
            RobinhoodRhHoldingDto h, BigDecimal qty, BigDecimal perSharePrice) {
        if (qty.compareTo(BigDecimal.ZERO) == 0 || perSharePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (isOption(h)) {
            BigDecimal markPerShare = perSharePrice.setScale(2, RoundingMode.HALF_UP);
            return scaleOptionMarketValue(
                    true, qty.abs().multiply(markPerShare).multiply(OPTION_CONTRACT_MULTIPLIER));
        }
        return qty.abs().multiply(perSharePrice).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleOptionMarketValue(boolean option, BigDecimal marketValue) {
        if (!option) {
            return scaleMoney(marketValue);
        }
        return nullToZero(marketValue).setScale(2, RoundingMode.HALF_UP);
    }

    private static RobinhoodRhHoldingDto withMarketValue(RobinhoodRhHoldingDto h, BigDecimal marketValue) {
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                h.quantity(),
                h.averageBuyPrice(),
                h.currentUnitPrice(),
                scaleOptionMarketValue(isOption(h), marketValue),
                h.costBasis(),
                h.unrealizedPnL(),
                h.unrealizedPnLPercent());
    }

    private static RobinhoodRhHoldingDto restoreMarketValueIfMissing(RobinhoodRhHoldingDto h) {
        if (!marketValueMissing(h)) {
            return h;
        }
        BigDecimal mv = effectiveMarketValue(h);
        if (mv.compareTo(BigDecimal.ZERO) > 0 && !marketValueEqualsCostBasis(h, mv)) {
            return withMarketValue(h, mv);
        }
        return h;
    }

    private static BigDecimal effectiveMarketValue(RobinhoodRhHoldingDto h) {
        BigDecimal mv = nullToZero(h.marketValue());
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return scaleOptionMarketValue(isOption(h), mv);
        }
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal unrealized = h.unrealizedPnL();
        // Only infer MV from cost + unrealized when unrealized is non-zero (synced P&L).
        // Placeholder unrealized=0 must not collapse to cost basis when quotes are missing.
        if (unrealized != null
                && unrealized.compareTo(BigDecimal.ZERO) != 0
                && cost.compareTo(BigDecimal.ZERO) > 0) {
            return cost.add(unrealized).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal unrealizedPnLPercent(BigDecimal unrealized, BigDecimal cost) {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unrealized
                .multiply(BigDecimal.valueOf(100))
                .divide(cost, 2, RoundingMode.HALF_UP);
    }

    private static boolean marketValueMissing(RobinhoodRhHoldingDto h) {
        BigDecimal mv = nullToZero(h.marketValue());
        if (mv.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        return "equity".equalsIgnoreCase(h.positionType()) && marketValueEqualsCostBasis(h, mv);
    }

    /** Stale sync rows often store qty × avg as market_value when live quotes were unavailable. */
    private static boolean marketValueEqualsCostBasis(RobinhoodRhHoldingDto h, BigDecimal marketValue) {
        BigDecimal cost = deriveCostBasis(h);
        if (cost.compareTo(BigDecimal.ZERO) <= 0 || marketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return marketValue.subtract(cost).abs().compareTo(new BigDecimal("0.05")) <= 0;
    }

    private static boolean isOption(RobinhoodRhHoldingDto h) {
        return isOptionType(h.positionType());
    }

    private static boolean isOptionType(String positionType) {
        return positionType != null && "option".equalsIgnoreCase(positionType);
    }

    private static BigDecimal decimalOrZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : nullToZero(v);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return nullToZero(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleUnitPrice(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return v.setScale(4, RoundingMode.HALF_UP);
    }
}
