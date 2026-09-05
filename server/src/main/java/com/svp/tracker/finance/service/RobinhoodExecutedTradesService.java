package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import com.svp.tracker.finance.dto.RobinhoodExecutedTradeDto;
import com.svp.tracker.finance.dto.RobinhoodExecutedTradesDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountColumnDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncedOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Year-long executed buy/sell list from synced Robinhood orders (filled only). */
@Service
@RequiredArgsConstructor
public class RobinhoodExecutedTradesService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final List<String> PREFERRED_SUFFIX_ORDER =
            List.of("3370", "3550", "4123", "8696", "4190", "7581");
    /** Individual, Agentic, Ammu — only these accounts appear on Trades. */
    private static final Set<String> TRADE_LIST_SUFFIXES = Set.of("3370", "3550", "8696");

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticSyncedOrderRepository syncedOrderRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;

    @Transactional(readOnly = true)
    public RobinhoodExecutedTradesDto build(int year) {
        long ownerUserId = currentUser.requireUserId();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<WorkingTrade> history = new ArrayList<>();
        Set<String> suffixes = new LinkedHashSet<>();
        Set<String> tradedSymbols = new LinkedHashSet<>();
        for (String pinned : TRADE_LIST_SUFFIXES) {
            if (accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, pinned)) {
                suffixes.add(pinned);
            }
        }
        for (RobinhoodAgenticSyncedOrder order :
                syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(ownerUserId)) {
            if (!isExecutedTrade(order.getState())) {
                continue;
            }
            String suffix = lastFour(order.getAccountNumber());
            if (suffix == null
                    || !TRADE_LIST_SUFFIXES.contains(suffix)
                    || !accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, suffix)) {
                continue;
            }
            Instant executedAt = order.getUpdatedAtRh() != null ? order.getUpdatedAtRh() : order.getCreatedAtRh();
            if (executedAt == null) {
                continue;
            }
            BigDecimal qty = order.getQuantity();
            BigDecimal price = order.getAveragePrice() != null ? order.getAveragePrice() : order.getLimitPrice();
            history.add(new WorkingTrade(
                    order.getSymbol(),
                    order.getSide(),
                    order.getOrderType(),
                    qty,
                    price,
                    notional(order.getSymbol(), qty, price),
                    order.getState(),
                    executedAt,
                    executedAt.atZone(CENTRAL).toLocalDate(),
                    suffix,
                    RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix)));
        }
        history.sort(Comparator.comparing(WorkingTrade::executedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, ArrayDeque<Lot>> books = new HashMap<>();
        List<RobinhoodExecutedTradeDto> trades = new ArrayList<>();
        for (WorkingTrade row : history) {
            RealizedPnl realized = null;
            String side = row.side() == null ? "" : row.side().trim().toLowerCase(Locale.ROOT);
            String bookKey = fifoKey(row.suffix(), row.symbol());
            if ("buy".equals(side)) {
                addLot(books.computeIfAbsent(bookKey, k -> new ArrayDeque<>()), row.symbol(), row.qty(), row.price());
            } else if ("sell".equals(side)) {
                realized = consumeSell(
                        books.computeIfAbsent(bookKey, k -> new ArrayDeque<>()),
                        row.symbol(),
                        row.qty(),
                        row.price());
            }
            if (row.tradeDate().isBefore(yearStart) || row.tradeDate().isAfter(yearEnd)) {
                continue;
            }
            String ticker = underlyingSymbol(row.symbol());
            if (ticker != null) {
                tradedSymbols.add(ticker);
            }
            trades.add(new RobinhoodExecutedTradeDto(
                    row.symbol(),
                    row.side(),
                    row.orderType(),
                    row.qty(),
                    scaleMoney(row.price()),
                    row.notional(),
                    row.state(),
                    row.executedAt(),
                    row.suffix(),
                    row.label(),
                    realized == null ? null : realized.pnl(),
                    realized == null ? null : realized.percent()));
        }
        trades.sort(Comparator.comparing(
                RobinhoodExecutedTradeDto::executedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<RobinhoodRhPeriodAccountColumnDto> accounts = orderSuffixes(suffixes).stream()
                .map(s -> new RobinhoodRhPeriodAccountColumnDto(
                        s, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(s)))
                .toList();

        List<String> symbolChoices = tradedSymbols.stream().sorted().toList();
        String note = trades.isEmpty()
                ? "No filled buys or sells in " + year + " yet. Sync Robinhood orders from Finance if this looks short."
                : "Filled and partially filled orders only, newest first. Times are America/Chicago. "
                        + "Individual, Agentic, and Ammu's accounts. Stock filter is names in the list below. "
                        + "Sell gain/loss is FIFO against earlier synced buys on the same account and symbol.";
        return new RobinhoodExecutedTradesDto(year, note, accounts, symbolChoices, trades);
    }

    static boolean isExecutedTrade(String state) {
        if (state == null) {
            return false;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        return s.equals("filled")
                || s.equals("partially_filled")
                || s.equals("completed")
                || s.equals("executed")
                || (s.contains("fill") && !s.contains("cancel") && !s.contains("reject"));
    }

    static String lastFour(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        return digits.substring(digits.length() - 4);
    }

    static String underlyingSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String trimmed = symbol.trim();
        int space = trimmed.indexOf(' ');
        String ticker = (space < 0 ? trimmed : trimmed.substring(0, space)).toUpperCase(Locale.ROOT);
        return ticker.isBlank() ? null : ticker;
    }

    static boolean isOptionSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String s = symbol.toUpperCase(Locale.ROOT);
        return s.contains(" CALL") || s.contains(" PUT") || s.contains(" $");
    }

    static BigDecimal notional(String symbol, BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null) {
            return null;
        }
        BigDecimal raw = quantity.multiply(price);
        if (isOptionSymbol(symbol)) {
            raw = raw.multiply(BigDecimal.valueOf(100));
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private static List<String> orderSuffixes(Set<String> suffixes) {
        List<String> out = new ArrayList<>();
        for (String preferred : PREFERRED_SUFFIX_ORDER) {
            if (suffixes.contains(preferred)) {
                out.add(preferred);
            }
        }
        suffixes.stream().filter(s -> !out.contains(s)).sorted().forEach(out::add);
        return out;
    }

    static void addLot(ArrayDeque<Lot> lots, String symbol, BigDecimal quantity, BigDecimal price) {
        if (lots == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 || price == null) {
            return;
        }
        BigDecimal cost = notional(symbol, quantity, price);
        if (cost == null) {
            return;
        }
        BigDecimal unit = cost.divide(quantity, 8, RoundingMode.HALF_UP);
        lots.addLast(new Lot(quantity, unit));
    }

    static RealizedPnl consumeSell(ArrayDeque<Lot> lots, String symbol, BigDecimal sellQty, BigDecimal sellPrice) {
        if (lots == null
                || sellQty == null
                || sellQty.compareTo(BigDecimal.ZERO) <= 0
                || sellPrice == null) {
            return new RealizedPnl(null, null);
        }
        BigDecimal remaining = sellQty;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal matched = BigDecimal.ZERO;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !lots.isEmpty()) {
            Lot lot = lots.removeFirst();
            BigDecimal take = lot.quantity().min(remaining);
            cost = cost.add(take.multiply(lot.unitCost()));
            matched = matched.add(take);
            BigDecimal leftover = lot.quantity().subtract(take);
            if (leftover.compareTo(BigDecimal.ZERO) > 0) {
                lots.addFirst(new Lot(leftover, lot.unitCost()));
            }
            remaining = remaining.subtract(take);
        }
        if (matched.compareTo(BigDecimal.ZERO) <= 0) {
            return new RealizedPnl(null, null);
        }
        BigDecimal proceeds = notional(symbol, matched, sellPrice);
        if (proceeds == null) {
            return new RealizedPnl(null, null);
        }
        BigDecimal pnl = proceeds.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = null;
        if (cost.compareTo(BigDecimal.ZERO) != 0) {
            percent = pnl.divide(cost, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new RealizedPnl(pnl, percent);
    }

    private static String fifoKey(String suffix, String symbol) {
        return (suffix == null ? "" : suffix)
                + "\0"
                + (symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT));
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    record WorkingTrade(
            String symbol,
            String side,
            String orderType,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal notional,
            String state,
            Instant executedAt,
            LocalDate tradeDate,
            String suffix,
            String label) {}

    record Lot(BigDecimal quantity, BigDecimal unitCost) {}

    record RealizedPnl(BigDecimal pnl, BigDecimal percent) {}

}
