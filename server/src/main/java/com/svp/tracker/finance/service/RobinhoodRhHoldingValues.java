package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
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
            YahooBatchQuoteService quoteService,
            RobinhoodRhLiveQuotesDto liveQuotes) {
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
        Map<String, String> optionInstrumentIds = RobinhoodRhHoldingQuoteService.instrumentIdsByMatchKey(positions);
        return finalizeHoldings(
                raw, accountEquityMarketValue, quoteService, liveQuotes, optionInstrumentIds);
    }

    static List<RobinhoodRhHoldingDto> finalizeHoldings(
            List<RobinhoodRhHoldingDto> holdings,
            BigDecimal accountEquityMarketValue,
            YahooBatchQuoteService quoteService) {
        return finalizeHoldings(
                holdings,
                accountEquityMarketValue,
                quoteService,
                RobinhoodRhLiveQuotesDto.empty(),
                Map.of());
    }

    static List<RobinhoodRhHoldingDto> finalizeHoldings(
            List<RobinhoodRhHoldingDto> holdings,
            BigDecimal accountEquityMarketValue,
            YahooBatchQuoteService quoteService,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhHoldingDto> working = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : holdings) {
            working.add(restoreMarketValueIfMissing(normalizeOptionTotalMarketValue(clearComputedFields(h))));
        }

        Map<String, YahooSimpleQuoteDto> quotes = fetchEquityQuotes(working, quoteService);
        Map<String, String> optionIds =
                optionInstrumentByMatchKey == null ? Map.of() : optionInstrumentByMatchKey;
        RobinhoodRhLiveQuotesDto live = liveQuotes == null ? RobinhoodRhLiveQuotesDto.empty() : liveQuotes;

        for (int i = 0; i < working.size(); i++) {
            working.set(i, applyLiveMarketValue(working.get(i), quotes, live, optionIds));
        }

        allocateStockEquityWhenNeeded(working, accountEquityMarketValue);

        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodRhHoldingDto h : working) {
            out.add(buildHolding(h, quotes, live, optionIds));
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
        return quoteService.fetchFreshBySymbols(List.copyOf(symbols));
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
            Map<String, YahooSimpleQuoteDto> yahooQuotes,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return h;
        }
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
            BigDecimal current = equityCurrentUnitPrice(h.symbol(), yahooQuotes, liveQuotes);
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                return withMarketValue(h, marketValueFromCurrent(qty, current));
            }
            return h;
        }
        if ("option".equalsIgnoreCase(h.positionType())) {
            String instrumentId = optionInstrumentByMatchKey.get(RobinhoodRhHoldingQuoteService.matchKey(h));
            if (instrumentId == null) {
                return h;
            }
            BigDecimal markPerShare = liveQuotes.optionMarkPerShareByInstrumentId().get(instrumentId);
            if (markPerShare == null || markPerShare.compareTo(BigDecimal.ZERO) <= 0) {
                return h;
            }
            BigDecimal current = optionCurrentUnitPrice(nullToZero(h.averageBuyPrice()), markPerShare);
            return withMarketValue(h, marketValueFromCurrent(qty, current));
        }
        return h;
    }

    private static BigDecimal equityCurrentUnitPrice(
            String symbol, Map<String, YahooSimpleQuoteDto> yahooQuotes, RobinhoodRhLiveQuotesDto liveQuotes) {
        if (symbol == null || symbol.isBlank()) {
            return BigDecimal.ZERO;
        }
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        BigDecimal rh = liveQuotes.equityPriceBySymbol().get(key);
        if (rh != null && rh.compareTo(BigDecimal.ZERO) > 0) {
            return rh;
        }
        YahooSimpleQuoteDto q = yahooQuotes.get(key);
        if (q != null && q.regularMarketPrice() != null && q.regularMarketPrice() > 0) {
            return BigDecimal.valueOf(q.regularMarketPrice());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal optionCurrentUnitPrice(BigDecimal averageBuyPrice, BigDecimal markPerShare) {
        if (markPerShare == null || markPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (averageBuyPrice != null && averageBuyPrice.compareTo(BigDecimal.valueOf(100)) > 0) {
            return markPerShare.multiply(BigDecimal.valueOf(100));
        }
        return markPerShare;
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

    private static RobinhoodRhHoldingDto buildHolding(
            RobinhoodRhHoldingDto h,
            Map<String, YahooSimpleQuoteDto> quotes,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        BigDecimal avg = scaleUnitPrice(nullToZero(h.averageBuyPrice()));
        BigDecimal cost = deriveCostBasis(withAverage(h, avg));
        BigDecimal current = resolveCurrentUnitPrice(h, quotes, liveQuotes, optionInstrumentByMatchKey);
        BigDecimal mv = marketValueFromCurrent(qty, current);
        if (mv.compareTo(BigDecimal.ZERO) == 0) {
            mv = effectiveMarketValue(h);
            current = resolveCurrentUnitPrice(withMarketValue(h, mv), quotes, liveQuotes, optionInstrumentByMatchKey);
            mv = marketValueFromCurrent(qty, current);
        }
        BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = unrealizedPnLPercent(unrealized, cost);
        BigDecimal currentUnit = qty.compareTo(BigDecimal.ZERO) > 0 && mv.compareTo(BigDecimal.ZERO) > 0
                ? mv.divide(qty.abs(), 4, RoundingMode.HALF_UP)
                : current;
        return new RobinhoodRhHoldingDto(
                h.symbol(),
                h.positionType(),
                qty,
                avg,
                scaleUnitPrice(currentUnit),
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
            Map<String, YahooSimpleQuoteDto> quotes,
            RobinhoodRhLiveQuotesDto liveQuotes,
            Map<String, String> optionInstrumentByMatchKey) {
        BigDecimal qty = nullToZero(h.quantity());
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if ("equity".equalsIgnoreCase(h.positionType()) && h.symbol() != null) {
            BigDecimal current = equityCurrentUnitPrice(h.symbol(), quotes, liveQuotes);
            if (current.compareTo(BigDecimal.ZERO) > 0) {
                return current;
            }
        }
        if ("option".equalsIgnoreCase(h.positionType())) {
            String instrumentId = optionInstrumentByMatchKey.get(RobinhoodRhHoldingQuoteService.matchKey(h));
            if (instrumentId != null) {
                BigDecimal markPerShare = liveQuotes.optionMarkPerShareByInstrumentId().get(instrumentId);
                BigDecimal current = optionCurrentUnitPrice(nullToZero(h.averageBuyPrice()), markPerShare);
                if (current.compareTo(BigDecimal.ZERO) > 0) {
                    return current;
                }
            }
        }
        BigDecimal mv = effectiveMarketValue(h);
        if (mv.compareTo(BigDecimal.ZERO) > 0) {
            return mv.divide(qty.abs(), 4, RoundingMode.HALF_UP);
        }
        BigDecimal cost = deriveCostBasis(h);
        BigDecimal unrealized = h.unrealizedPnL();
        if (unrealized != null
                && unrealized.compareTo(BigDecimal.ZERO) != 0
                && qty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal implied = cost.add(unrealized);
            if (implied.compareTo(BigDecimal.ZERO) > 0) {
                return implied.divide(qty.abs(), 4, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
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
            return qty.abs().multiply(avg).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal stored = nullToZero(h.costBasis());
        if (stored.compareTo(BigDecimal.ZERO) > 0) {
            return stored.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
        if (mv.compareTo(BigDecimal.ZERO) > 0 && !marketValueEqualsCostBasis(h, mv)) {
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
