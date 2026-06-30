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
            BigDecimal mv = decimalOrZero(p.getMarketValue());
            BigDecimal cost = costBasis(qty, avg, null);
            BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
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
        allocateAccountEquityWhenNeeded(enriched, accountEquityMarketValue);
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

    private static void allocateAccountEquityWhenNeeded(
            List<RobinhoodRhHoldingDto> holdings, BigDecimal accountEquityMarketValue) {
        BigDecimal equityMv = nullToZero(accountEquityMarketValue);
        if (equityMv.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal sumMv = holdings.stream()
                .map(h -> nullToZero(h.marketValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumMv.compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        BigDecimal totalCost = holdings.stream()
                .map(h -> costBasis(nullToZero(h.quantity()), nullToZero(h.averageBuyPrice()), h.costBasis()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < holdings.size(); i++) {
                RobinhoodRhHoldingDto h = holdings.get(i);
                BigDecimal cost = costBasis(nullToZero(h.quantity()), nullToZero(h.averageBuyPrice()), h.costBasis());
                BigDecimal mv;
                if (i == holdings.size() - 1) {
                    mv = equityMv.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
                } else {
                    mv = equityMv.multiply(cost).divide(totalCost, 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(mv);
                }
                holdings.set(i, withMarketValue(h, mv));
            }
            return;
        }
        if (holdings.size() == 1) {
            holdings.set(0, withMarketValue(holdings.get(0), equityMv));
        }
    }

    private static RobinhoodRhHoldingDto enrichOne(
            RobinhoodRhHoldingDto h, Map<String, YahooSimpleQuoteDto> quotes) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = nullToZero(h.averageBuyPrice());
        BigDecimal cost = costBasis(qty, avg, h.costBasis());
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
                mv = qty.abs()
                        .multiply(avg)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        return withMarketValue(h, mv);
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
