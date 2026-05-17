package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.RobinhoodClosedTradeDto;
import com.svp.tracker.finance.dto.RobinhoodDailyPnLPointDto;
import com.svp.tracker.finance.dto.RobinhoodEquityCurvePointDto;
import com.svp.tracker.finance.dto.RobinhoodInstrumentPerformanceDto;
import com.svp.tracker.finance.dto.RobinhoodMonthlyPnLPointDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceInsightsDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceReportDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodPerformanceTaxDto;
import com.svp.tracker.finance.dto.RobinhoodQuarterlyGainDto;
import com.svp.tracker.finance.dto.RobinhoodStrategyPerformanceDto;
import com.svp.tracker.finance.dto.RobinhoodTradingFrequencyDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds performance reports from imported Robinhood rows using FIFO realized P&amp;L on buy/sell legs (BTO/Buy,
 * STC/Sell, etc.). Not tax advice; wash sales and options assignments are not modeled.
 */
@Service
@RequiredArgsConstructor
public class RobinhoodPerformanceReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal PNL_EPSILON = new BigDecimal("0.01");
    private static final int TOP_INSTRUMENTS = 10;
    private static final int WORST_TRADES = 10;

    private final RobinhoodFinanceService robinhoodFinanceService;
    private final FinanceProperties financeProperties;

    public RobinhoodPerformanceReportDto buildReport(int financialYear, String symbolFilter) {
        List<Map<String, Object>> rows = robinhoodFinanceService.loadYearTransactionRows(financialYear, symbolFilter);
        int cap = financeProperties.maxStocksSummaryRows();
        boolean truncated = rows.size() >= cap;

        FifoResult fifo = runFifo(rows);

        List<RobinhoodDailyPnLPointDto> daily = new ArrayList<>();
        BigDecimal cumulative = ZERO;
        List<RobinhoodEquityCurvePointDto> equity = new ArrayList<>();
        for (Map.Entry<LocalDate, DayAgg> e : fifo.byDay().entrySet()) {
            daily.add(new RobinhoodDailyPnLPointDto(e.getKey(), e.getValue().pnl, e.getValue().closes));
            cumulative = cumulative.add(e.getValue().pnl);
            equity.add(new RobinhoodEquityCurvePointDto(e.getKey(), cumulative));
        }

        Map<String, BigDecimal> monthlyMap = new TreeMap<>();
        for (RobinhoodDailyPnLPointDto d : daily) {
            String ym = d.date().toString().substring(0, 7);
            monthlyMap.merge(ym, d.realizedPnL(), BigDecimal::add);
        }
        List<RobinhoodMonthlyPnLPointDto> monthly = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : monthlyMap.entrySet()) {
            monthly.add(new RobinhoodMonthlyPnLPointDto(e.getKey(), formatYearMonth(e.getKey()), e.getValue()));
        }

        RobinhoodPerformanceSummaryDto summary =
                buildSummary(fifo.byDay(), fifo.winCount(), fifo.lossCount(), fifo.breakevenCount());
        List<RobinhoodClosedTradeDto> closedTrades =
                fifo.closedTrades().stream().map(this::toClosedTradeDto).toList();
        RobinhoodPerformanceInsightsDto insights = buildInsights(fifo.closedTrades(), financialYear);
        RobinhoodPerformanceTaxDto tax = buildTax(fifo.closedTrades(), financialYear);

        String note =
                "Realized P&L uses FIFO matching on buy/sell legs (BTO/Buy vs STC/Sell). Dividends, ACH, and other codes are excluded. Not tax advice.";
        if (truncated) {
            note += " Analysis capped at " + cap + " rows; totals may be incomplete.";
        }

        return new RobinhoodPerformanceReportDto(
                financialYear,
                symbolFilter != null && !symbolFilter.isBlank() ? symbolFilter.trim() : null,
                financeProperties.robinhoodTable(),
                rows.size(),
                truncated,
                note,
                summary,
                daily,
                monthly,
                equity,
                closedTrades,
                insights,
                tax);
    }

    /** All FIFO closed lots for notebook export (same filters as {@link #buildReport}). */
    public List<RobinhoodClosedTradeDto> listClosedTrades(int financialYear, String symbolFilter) {
        List<Map<String, Object>> rows = robinhoodFinanceService.loadYearTransactionRows(financialYear, symbolFilter);
        FifoResult fifo = runFifo(rows);
        return fifo.closedTrades().stream().map(this::toClosedTradeDto).toList();
    }

    private FifoResult runFifo(List<Map<String, Object>> rows) {
        List<TradeEvent> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            TradeEvent ev = toTradeEvent(row);
            if (ev != null) {
                events.add(ev);
            }
        }
        events.sort(
                Comparator.comparing(TradeEvent::activityDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TradeEvent::leg));

        Map<String, Deque<Lot>> books = new LinkedHashMap<>();
        Map<LocalDate, DayAgg> byDay = new TreeMap<>();
        List<ClosedTrade> closedTrades = new ArrayList<>();
        int winCount = 0;
        int lossCount = 0;
        int breakevenCount = 0;

        for (TradeEvent ev : events) {
            Deque<Lot> lots = books.computeIfAbsent(ev.positionKey(), k -> new ArrayDeque<>());
            if (ev.leg() == Leg.BUY) {
                BigDecimal buyQty = ev.quantity().abs();
                if (buyQty.compareTo(PNL_EPSILON) > 0) {
                    lots.addLast(new Lot(buyQty, ev.amount().abs(), ev.activityDate()));
                }
                continue;
            }
            BigDecimal sellQty = ev.quantity().abs();
            BigDecimal sellCash = ev.amount();
            if (sellQty.compareTo(PNL_EPSILON) <= 0 || ev.activityDate() == null) {
                continue;
            }
            BigDecimal remaining = sellQty;
            while (remaining.compareTo(PNL_EPSILON) > 0 && !lots.isEmpty()) {
                Lot lot = lots.peekFirst();
                if (lot.quantity.compareTo(PNL_EPSILON) <= 0) {
                    lots.removeFirst();
                    continue;
                }
                BigDecimal take = remaining.min(lot.quantity);
                BigDecimal proceeds = sellCash.multiply(take).divide(sellQty, 8, RoundingMode.HALF_UP);
                BigDecimal cost = lot.costTotal.multiply(take).divide(lot.quantity, 8, RoundingMode.HALF_UP);
                BigDecimal realized = proceeds.subtract(cost);
                LocalDate buyDate = lot.openedAt != null ? lot.openedAt : ev.activityDate();
                int holdDays = (int) Math.max(0, ChronoUnit.DAYS.between(buyDate, ev.activityDate()));

                lot.quantity = lot.quantity.subtract(take);
                lot.costTotal = lot.costTotal.subtract(cost);
                if (lot.quantity.compareTo(PNL_EPSILON) <= 0) {
                    lots.removeFirst();
                }
                remaining = remaining.subtract(take);

                recordClose(byDay, ev.activityDate(), realized);
                int[] counts = classifyCloseMutable(realized);
                winCount += counts[0];
                lossCount += counts[1];
                breakevenCount += counts[2];
                closedTrades.add(
                        new ClosedTrade(
                                ev.instrument(),
                                ev.contract(),
                                ev.strategy(),
                                buyDate,
                                ev.activityDate(),
                                holdDays,
                                take,
                                realized));
            }
            if (remaining.compareTo(PNL_EPSILON) > 0 && sellQty.compareTo(PNL_EPSILON) > 0) {
                BigDecimal proceeds = sellCash.multiply(remaining).divide(sellQty, 8, RoundingMode.HALF_UP);
                recordClose(byDay, ev.activityDate(), proceeds);
                int[] counts = classifyCloseMutable(proceeds);
                winCount += counts[0];
                lossCount += counts[1];
                breakevenCount += counts[2];
                closedTrades.add(
                        new ClosedTrade(
                                ev.instrument(),
                                ev.contract(),
                                ev.strategy(),
                                ev.activityDate(),
                                ev.activityDate(),
                                0,
                                remaining,
                                proceeds));
            }
        }
        return new FifoResult(byDay, closedTrades, winCount, lossCount, breakevenCount);
    }

    private RobinhoodPerformanceInsightsDto buildInsights(List<ClosedTrade> closed, int year) {
        Map<String, InstrumentAgg> byInstrument = new LinkedHashMap<>();
        for (ClosedTrade c : closed) {
            InstrumentAgg agg = byInstrument.computeIfAbsent(c.instrument(), k -> new InstrumentAgg(c.instrument()));
            agg.total = agg.total.add(c.realizedPnL());
            agg.closes++;
            if (c.realizedPnL().compareTo(PNL_EPSILON) > 0) {
                agg.wins++;
            } else if (c.realizedPnL().compareTo(PNL_EPSILON.negate()) < 0) {
                agg.losses++;
            }
        }
        List<RobinhoodInstrumentPerformanceDto> best =
                byInstrument.values().stream()
                        .sorted(Comparator.comparing((InstrumentAgg a) -> a.total).reversed())
                        .limit(TOP_INSTRUMENTS)
                        .map(a -> new RobinhoodInstrumentPerformanceDto(a.instrument, a.total, a.closes, a.wins, a.losses))
                        .toList();

        List<RobinhoodClosedTradeDto> worst =
                closed.stream()
                        .sorted(Comparator.comparing(ClosedTrade::realizedPnL))
                        .limit(WORST_TRADES)
                        .map(this::toClosedTradeDto)
                        .toList();

        List<Integer> holdDays =
                closed.stream().map(ClosedTrade::holdDays).filter(d -> d >= 0).sorted().toList();
        double avgHold = holdDays.isEmpty() ? 0.0 : holdDays.stream().mapToInt(i -> i).average().orElse(0);
        int medianHold = median(holdDays);

        RobinhoodTradingFrequencyDto frequency = buildFrequency(closed, year);
        List<RobinhoodStrategyPerformanceDto> strategies = buildStrategyPerformance(closed);

        return new RobinhoodPerformanceInsightsDto(best, worst, avgHold, medianHold, frequency, strategies);
    }

    private RobinhoodTradingFrequencyDto buildFrequency(List<ClosedTrade> closed, int year) {
        int total = closed.size();
        if (total == 0) {
            return new RobinhoodTradingFrequencyDto(0, 0, 0, 0, "—", 0);
        }
        Map<LocalDate, Integer> bySellDay = new TreeMap<>();
        Map<String, Integer> byMonth = new TreeMap<>();
        for (ClosedTrade c : closed) {
            if (c.sellDate() != null) {
                bySellDay.merge(c.sellDate(), 1, Integer::sum);
                String ym = c.sellDate().toString().substring(0, 7);
                byMonth.merge(ym, 1, Integer::sum);
            }
        }
        int tradingDays = bySellDay.size();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        long weeks = Math.max(1, ChronoUnit.WEEKS.between(yearStart, yearEnd) + 1);
        double perWeek = (double) total / weeks;
        double perMonth = total / 12.0;

        String busiestLabel = "—";
        int busiestCount = 0;
        for (Map.Entry<String, Integer> e : byMonth.entrySet()) {
            if (e.getValue() > busiestCount) {
                busiestCount = e.getValue();
                busiestLabel = formatYearMonth(e.getKey());
            }
        }
        return new RobinhoodTradingFrequencyDto(
                total, tradingDays, round2(perWeek), round2(perMonth), busiestLabel, busiestCount);
    }

    private List<RobinhoodStrategyPerformanceDto> buildStrategyPerformance(List<ClosedTrade> closed) {
        Map<String, StrategyAgg> map = new LinkedHashMap<>();
        for (ClosedTrade c : closed) {
            String key = c.strategy().label();
            StrategyAgg agg = map.computeIfAbsent(key, k -> new StrategyAgg(key));
            agg.total = agg.total.add(c.realizedPnL());
            agg.closes++;
            if (c.realizedPnL().compareTo(PNL_EPSILON) > 0) {
                agg.wins++;
            } else if (c.realizedPnL().compareTo(PNL_EPSILON.negate()) < 0) {
                agg.losses++;
            }
        }
        return map.values().stream()
                .sorted(Comparator.comparing((StrategyAgg a) -> a.total).reversed())
                .map(a -> {
                    int decisive = a.wins + a.losses;
                    double rate = decisive == 0 ? 0.0 : (double) a.wins / decisive;
                    return new RobinhoodStrategyPerformanceDto(a.label, a.total, a.closes, rate);
                })
                .toList();
    }

    private RobinhoodPerformanceTaxDto buildTax(List<ClosedTrade> closed, int year) {
        double rate = financeProperties.robinhoodEstimatedTaxRate();
        BigDecimal[] quarterGain = {ZERO, ZERO, ZERO, ZERO};
        for (ClosedTrade c : closed) {
            if (c.sellDate() == null || c.sellDate().getYear() != year) {
                continue;
            }
            int q = (c.sellDate().getMonthValue() - 1) / 3;
            quarterGain[q] = quarterGain[q].add(c.realizedPnL());
        }
        List<RobinhoodQuarterlyGainDto> quarterly = new ArrayList<>();
        BigDecimal yearGain = ZERO;
        for (int i = 0; i < 4; i++) {
            BigDecimal gain = quarterGain[i];
            yearGain = yearGain.add(gain);
            BigDecimal tax =
                    gain.compareTo(ZERO) > 0
                            ? gain.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP)
                            : ZERO;
            quarterly.add(
                    new RobinhoodQuarterlyGainDto(
                            i + 1, "Q" + (i + 1) + " " + year, gain, tax));
        }
        BigDecimal yearTax =
                yearGain.compareTo(ZERO) > 0
                        ? yearGain.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP)
                        : ZERO;
        String disclaimer =
                "Estimated tax applies a flat "
                        + Math.round(rate * 100)
                        + "% rate to positive realized gains per quarter (configure tracker.finance.robinhood-estimated-tax-rate). "
                        + "Does not model wash sales, state tax, withholding, or short- vs long-term rates. Not tax advice.";
        return new RobinhoodPerformanceTaxDto(quarterly, yearGain, rate, yearTax, disclaimer);
    }

    private RobinhoodClosedTradeDto toClosedTradeDto(ClosedTrade c) {
        return new RobinhoodClosedTradeDto(
                c.instrument(),
                c.contract(),
                c.strategy().label(),
                c.buyDate(),
                c.sellDate(),
                c.holdDays(),
                c.quantity(),
                c.realizedPnL());
    }

    private static int median(List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static RobinhoodPerformanceSummaryDto buildSummary(
            Map<LocalDate, DayAgg> byDay, int winCount, int lossCount, int breakevenCount) {
        BigDecimal total = ZERO;
        LocalDate bestDay = null;
        BigDecimal bestPnL = null;
        LocalDate worstDay = null;
        BigDecimal worstPnL = null;
        int tradingDays = 0;
        for (Map.Entry<LocalDate, DayAgg> e : byDay.entrySet()) {
            BigDecimal p = e.getValue().pnl;
            if (p.abs().compareTo(PNL_EPSILON) < 0) {
                continue;
            }
            tradingDays++;
            total = total.add(p);
            if (bestPnL == null || p.compareTo(bestPnL) > 0) {
                bestPnL = p;
                bestDay = e.getKey();
            }
            if (worstPnL == null || p.compareTo(worstPnL) < 0) {
                worstPnL = p;
                worstDay = e.getKey();
            }
        }
        int decisive = winCount + lossCount;
        double winRate = decisive == 0 ? 0.0 : (double) winCount / decisive;
        return new RobinhoodPerformanceSummaryDto(
                total,
                winCount,
                lossCount,
                breakevenCount,
                winRate,
                tradingDays,
                bestDay,
                bestPnL != null ? bestPnL : ZERO,
                worstDay,
                worstPnL != null ? worstPnL : ZERO);
    }

    private static void recordClose(Map<LocalDate, DayAgg> byDay, LocalDate date, BigDecimal realized) {
        if (date == null) {
            return;
        }
        DayAgg agg = byDay.computeIfAbsent(date, d -> new DayAgg());
        agg.pnl = agg.pnl.add(realized);
        agg.closes++;
    }

    private static int[] classifyCloseMutable(BigDecimal realized) {
        if (realized.compareTo(PNL_EPSILON) > 0) {
            return new int[] {1, 0, 0};
        }
        if (realized.compareTo(PNL_EPSILON.negate()) < 0) {
            return new int[] {0, 1, 0};
        }
        return new int[] {0, 0, 1};
    }

    private static String formatYearMonth(String ym) {
        try {
            int y = Integer.parseInt(ym.substring(0, 4));
            int mo = Integer.parseInt(ym.substring(5, 7));
            return Month.of(mo).getDisplayName(TextStyle.FULL, Locale.US) + " " + y;
        } catch (Exception e) {
            return ym;
        }
    }

    private TradeEvent toTradeEvent(Map<String, Object> row) {
        String trans = RobinhoodFinanceService.stringCellPublic(row, "TRANS_CODE");
        Leg leg = classifyLeg(trans);
        if (leg == Leg.OTHER) {
            return null;
        }
        LocalDate activity = RobinhoodFinanceService.localDateCellPublic(row, "ACTIVITY_DATE");
        String inst = Objects.requireNonNullElse(RobinhoodFinanceService.stringCellPublic(row, "INSTRUMENT"), "")
                .trim();
        if (inst.isEmpty()) {
            inst = "—";
        }
        String contract = Objects.requireNonNullElse(RobinhoodFinanceService.stringCellPublic(row, "DESCRIPTION"), "")
                .trim();
        if (contract.isEmpty()) {
            contract = "—";
        }
        BigDecimal qty = RobinhoodFinanceService.decimalCellPublic(row, "QUANTITY");
        BigDecimal amt = RobinhoodFinanceService.decimalCellPublic(row, "AMOUNT");
        TradeStrategy strategy = strategyFromTrans(trans);
        return new TradeEvent(
                inst + "\u0001" + contract, inst, contract, strategy, leg, activity, qty, amt);
    }

    private static TradeStrategy strategyFromTrans(String transCode) {
        if (transCode == null) {
            return TradeStrategy.OTHER;
        }
        return switch (transCode.trim().toUpperCase(Locale.ROOT)) {
            case "BTO", "STC", "STO", "BTC" -> TradeStrategy.OPTION;
            case "BUY", "SELL" -> TradeStrategy.STOCK;
            default -> TradeStrategy.OTHER;
        };
    }

    private static Leg classifyLeg(String transCode) {
        if (transCode == null) {
            return Leg.OTHER;
        }
        String u = transCode.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "BTO", "BTC", "BUY" -> Leg.BUY;
            case "STC", "STO", "SELL" -> Leg.SELL;
            default -> Leg.OTHER;
        };
    }

    private record FifoResult(
            Map<LocalDate, DayAgg> byDay,
            List<ClosedTrade> closedTrades,
            int winCount,
            int lossCount,
            int breakevenCount) {}

    private record ClosedTrade(
            String instrument,
            String contract,
            TradeStrategy strategy,
            LocalDate buyDate,
            LocalDate sellDate,
            int holdDays,
            BigDecimal quantity,
            BigDecimal realizedPnL) {}

    private enum TradeStrategy {
        STOCK("Stock"),
        OPTION("Option"),
        OTHER("Other");

        private final String label;

        TradeStrategy(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private enum Leg {
        BUY,
        SELL,
        OTHER
    }

    private record TradeEvent(
            String positionKey,
            String instrument,
            String contract,
            TradeStrategy strategy,
            Leg leg,
            LocalDate activityDate,
            BigDecimal quantity,
            BigDecimal amount) {}

    private static final class Lot {
        BigDecimal quantity;
        BigDecimal costTotal;
        LocalDate openedAt;

        Lot(BigDecimal quantity, BigDecimal costTotal, LocalDate openedAt) {
            this.quantity = quantity;
            this.costTotal = costTotal;
            this.openedAt = openedAt;
        }
    }

    private static final class DayAgg {
        BigDecimal pnl = ZERO;
        int closes;
    }

    private static final class InstrumentAgg {
        final String instrument;
        BigDecimal total = ZERO;
        int closes;
        int wins;
        int losses;

        InstrumentAgg(String instrument) {
            this.instrument = instrument;
        }
    }

    private static final class StrategyAgg {
        final String label;
        BigDecimal total = ZERO;
        int closes;
        int wins;
        int losses;

        StrategyAgg(String label) {
            this.label = label;
        }
    }
}
