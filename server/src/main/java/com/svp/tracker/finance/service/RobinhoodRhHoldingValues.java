package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.YahooSimpleQuoteDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Derives per-position market value, cost basis, and unrealized P&amp;L for RH holdings displays. */
final class RobinhoodRhHoldingValues {

    private static final BigDecimal OPTION_INFLATION_RATIO_LOW = BigDecimal.valueOf(75);
    private static final BigDecimal OPTION_INFLATION_RATIO_HIGH = BigDecimal.valueOf(125);

    private RobinhoodRhHoldingValues() {}

    static List<RobinhoodRhHoldingDto> fromPositions(
            List<RobinhoodAgenticPosition> positions,
            BigDecimal accountEquityMarketValue,
            YahooBatchQuoteService quoteService) {
        List<RobinhoodRhHoldingDto> raw = new ArrayList<>();
        for (RobinhoodAgenticPosition p : positions) {
            BigDecimal qty = nullToZero(p.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal avg = nullToZero(p.getAverageBuyPrice());
            raw.add(new RobinhoodRhHoldingDto(
                    p.getSymbol(),
                    p.getPositionType(),
                    qty,
                    scaleUnitPrice(avg),
                    BigDecimal.ZERO,
                    decimalOrZero(p.getMarketValue()),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO));
        }
        raw.sort(Comparator.comparing(RobinhoodRhHoldingDto::symbol, String.CASE_INSENSITIVE_ORDER));
        return finalizeHoldings(raw, accountEquityMarketValue, quoteService);
    }

    static List<RobinhoodRhHoldingDto> finalizeHoldings(
            List<RobinhoodRhHoldingDto> holdings,
            BigDecimal accountEquityMarketValue,
            YahooBatchQuoteService quoteService) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhHoldingDto> working = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            working.add(restoreMarketValueIfMissing(normalizeOptionTotalMarketValue(h)));
        }

        Map<String, YahooSimpleQuoteDto> quotes = fetchEquityQuotes(working, quoteService);

        for (int i = 0; i < working.size(); i++) {
            working.set(i, applyEquityQuoteMarketValue(working.get(i), quotes));
        }

        allocateStockEquityWhenNeeded(working, accountEquityMarketValue);

        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : working) {
            out.add(buildHolding(h, quotes));
        }
        return out;
    }

    private static Map<String, YahooSimpleQuoteDto> fetchEquityQuotes(
            List<RobinhoodRhHoldingDto> holdings, YahooBatchQuoteService quoteService) {
        if (quoteService == null) {
            return Map.of();
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null && !h.symbol().isBlank()) {
                symbols.add(h.symbol().trim().toUpperCase(Locale.ROOT));
            }
        }
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return quoteService.fetchBySymbols(List.copyOf(symbols));
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

    private static RobinhoodRhHoldingDto applyEquityQuoteMarketValue(
            RobinhoodRhHoldingDto h, Map<String, YahooSimpleQuoteDto> quotes) {
        if (!"equity".equalsIgnoreCase(h.positionType()) || h.symbol() == null) {
            return h;
        }
        if (!marketValueMissing(h)) {
            return h;
        }
        YahooSimpleQuoteDto q = quotes.get(h.symbol().trim().toUpperCase(Locale.ROOT));
        if (q == null || q.regularMarketPrice() == null || q.regularMarketPrice() <= 0) {
            return h;
        }
        BigDecimal current = BigDecimal.valueOf(q.regularMarketPrice());
        BigDecimal mv = marketValueFromCurrent(nullToZero(h.quantity()), current);
        return withMarketValue(h, mv);
    }

    private static RobinhoodRhHoldingDto normalizeOptionTotalMarketValue(RobinhoodRhHoldingDto h) {
        if (!"option".equalsIgnoreCase(h.positionType())) {
            return h;
        }
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal mv = nullToZero(h.marketValue());

        if (cost.compareTo(BigDecimal.ZERO) > 0 && mv.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = mv.divide(cost, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(OPTION_INFLATION_RATIO_LOW) >= 0
                    && ratio.compareTo(OPTION_INFLATION_RATIO_HIGH) <= 0) {
                return withMarketValue(h, mv.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }
        return h;
    }

    private static RobinhoodRhHoldingDto buildHolding(RobinhoodRhHoldingDto h, Map<String, YahooSimpleQuoteDto> quotes) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = scaleUnitPrice(nullToZero(h.averageBuyPrice()));
        BigDecimal cost = deriveCostBasis(withAverage(h, avg));
        BigDecimal current = resolveCurrentUnitPrice(h, quotes);
        BigDecimal mv = marketValueFromCurrent(qty, current);
        if (mv.compareTo(BigDecimal.ZERO) == 0) {
            mv = effectiveMarketValue(h);
            current = resolveCurrentUnitPrice(withMarketValue(h, mv), quotes);
            mv = marketValueFromCurrent(qty, current);
        }
        BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = unrealizedPnLPercent(unrealized, cost);
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                qty,
                avg,
                scaleUnitPrice(current),
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
            RobinhoodRhHoldingDto h, Map<String, YahooSimpleQuoteDto> quotes) {
        BigDecimal qty = nullToZero(h.quantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
            YahooSimpleQuoteDto q = quotes.get(h.symbol().trim().toUpperCase(Locale.ROOT));
            if (q != null && q.regularMarketPrice() != null && q.regularMarketPrice() > 0) {
                return BigDecimal.valueOf(q.regularMarketPrice());
            }
        }
        BigDecimal mv = effectiveMarketValue(h);
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return mv.divide(qty.abs(), 4, RoundingMode.HALF_UP);
        }
        if (h.currentUnitPrice() != null && h.currentUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return h.currentUnitPrice();
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal deriveCostBasis(RobinhoodRhHoldingDto h) {
        BigDecimal stored = nullToZero(h.costBasis());
        if (stored.compareTo(BigDecimal.ZERO) > 0) {
            return stored.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        if (qty.compareTo(BigDecimal.ZERO) == 0 || avg.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return qty.abs().multiply(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal marketValueFromCurrent(BigDecimal qty, BigDecimal currentUnitPrice) {
        if (qty.compareTo(BigDecimal.ZERO) == 0 || currentUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return qty.abs().multiply(currentUnitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private static RobinhoodRhHoldingDto withMarketValue(RobinhoodRhHoldingDto h, BigDecimal marketValue) {
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                h.quantity(),
                h.averageBuyPrice(),
                h.currentUnitPrice(),
                scaleMoney(marketValue),
                h.costBasis(),
                h.unrealizedPnL(),
                h.unrealizedPnLPercent());
    }

    private static RobinhoodRhHoldingDto restoreMarketValueIfMissing(RobinhoodRhHoldingDto h) {
        if (!marketValueMissing(h)) {
            return h;
        }
        BigDecimal mv = effectiveMarketValue(h);
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return withMarketValue(h, mv);
        }
        return h;
    }

    private static BigDecimal effectiveMarketValue(RobinhoodRhHoldingDto h) {
        BigDecimal mv = nullToZero(h.marketValue());
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return mv;
        }
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal unrealized = h.unrealizedPnL();
        if (unrealized != null
                && (cost.compareTo(BigDecimal.ZERO) > 0 || unrealized.compareTo(BigDecimal.ZERO) != 0)) {
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
        return nullToZero(h.marketValue()).compareTo(BigDecimal.ZERO) == 0;
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
