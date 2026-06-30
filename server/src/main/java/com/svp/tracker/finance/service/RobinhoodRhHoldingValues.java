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
            BigDecimal mv = normalizeOptionMarketValue(
                            new RobinhoodRhHoldingDto(
                                    p.getSymbol(),
                                    p.getPositionType(),
                                    qty,
                                    avg,
                                    decimalOrZero(p.getMarketValue()),
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO))
                    .marketValue();
            BigDecimal cost = costBasis(qty, avg, null);
            BigDecimal unrealized = nullToZero(mv).subtract(cost).setScale(2, RoundingMode.HALF_UP);
            raw.add(new RobinhoodRhHoldingDto(
                    p.getSymbol(),
                    p.getPositionType(),
                    qty,
                    avg,
                    scaleMoney(mv),
                    scaleMoney(cost),
                    scaleMoney(unrealized)));
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
        Map<String, YahooSimpleQuoteDto> quotes = fetchQuotesForZeroMarketValues(holdings, quoteService);
        List<RobinhoodRhHoldingDto> enriched = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            enriched.add(enrichOne(h, quotes));
        }
        allocateStockEquityWhenNeeded(enriched, accountEquityMarketValue);
        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : enriched) {
            out.add(reconcile(h));
        }
        return out;
    }

    private static Map<String, YahooSimpleQuoteDto> fetchQuotesForZeroMarketValues(
            List<RobinhoodRhHoldingDto> holdings, YahooBatchQuoteService quoteService) {
        if (quoteService == null) {
            return Map.of();
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            if (!"equity".equalsIgnoreCase(h.positionType())) {
                continue;
            }
            if (marketValueMissing(h) && h.symbol() != null && !h.symbol().isBlank()) {
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
            totalCost = totalCost.add(
                    costBasis(nullToZero(h.quantity()), nullToZero(h.averageBuyPrice()), h.costBasis()));
        }

        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int j = 0; j < equityNeeding.size(); j++) {
                int i = equityNeeding.get(j);
                RobinhoodRhHoldingDto h = holdings.get(i);
                BigDecimal cost = costBasis(
                        nullToZero(h.quantity()), nullToZero(h.averageBuyPrice()), h.costBasis());
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

    private static RobinhoodRhHoldingDto enrichOne(
            RobinhoodRhHoldingDto h, Map<String, YahooSimpleQuoteDto> quotes) {
        h = normalizeOptionMarketValue(h);
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        BigDecimal mv = nullToZero(h.marketValue());
        if (marketValueMissing(h) && qty.compareTo(BigDecimal.ZERO) != 0) {
            if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
                YahooSimpleQuoteDto q = quotes.get(h.symbol().trim().toUpperCase(Locale.ROOT));
                if (q != null && q.regularMarketPrice() != null && q.regularMarketPrice() > 0) {
                    mv = BigDecimal.valueOf(q.regularMarketPrice())
                            .multiply(qty.abs())
                            .setScale(2, RoundingMode.HALF_UP);
                }
            } else if ("option".equalsIgnoreCase(h.positionType()) && avg.compareTo(BigDecimal.ZERO) > 0) {
                mv = optionMarkFromAverage(qty, avg);
            }
        }
        return withMarketValue(h, mv);
    }

    private static RobinhoodRhHoldingDto normalizeOptionMarketValue(RobinhoodRhHoldingDto h) {
        if (!"option".equalsIgnoreCase(h.positionType())) {
            return h;
        }
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        BigDecimal cost = costBasis(qty, avg, h.costBasis());
        BigDecimal mv = nullToZero(h.marketValue());

        if (cost.compareTo(BigDecimal.ZERO) > 0 && mv.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = mv.divide(cost, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(OPTION_INFLATION_RATIO_LOW) >= 0
                    && ratio.compareTo(OPTION_INFLATION_RATIO_HIGH) <= 0) {
                return withMarketValue(h, mv.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }

        if (marketValueMissing(h) && avg.compareTo(BigDecimal.ZERO) > 0) {
            return withMarketValue(h, optionMarkFromAverage(qty, avg));
        }
        return h;
    }

    /** Contract dollars when average_buy_price already matches qty × avg cost basis (no ×100). */
    private static BigDecimal optionMarkFromAverage(BigDecimal qty, BigDecimal avg) {
        return qty.abs().multiply(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private static RobinhoodRhHoldingDto reconcile(RobinhoodRhHoldingDto h) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        BigDecimal cost = costBasis(qty, avg, h.costBasis());
        BigDecimal mv = nullToZero(h.marketValue());
        BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                qty,
                avg,
                scaleMoney(mv),
                scaleMoney(cost),
                scaleMoney(unrealized));
    }

    private static RobinhoodRhHoldingDto withMarketValue(RobinhoodRhHoldingDto h, BigDecimal marketValue) {
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                h.quantity(),
                h.averageBuyPrice(),
                scaleMoney(marketValue),
                h.costBasis(),
                h.unrealizedPnL());
    }

    private static boolean marketValueMissing(RobinhoodRhHoldingDto h) {
        return nullToZero(h.marketValue()).compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal costBasis(BigDecimal qty, BigDecimal avg, BigDecimal stored) {
        BigDecimal cost = nullToZero(stored);
        if (cost.compareTo(BigDecimal.ZERO) == 0 && qty.compareTo(BigDecimal.ZERO) != 0 && avg.compareTo(BigDecimal.ZERO) != 0) {
            cost = qty.abs().multiply(avg).setScale(2, RoundingMode.HALF_UP);
        }
        return cost;
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
}
