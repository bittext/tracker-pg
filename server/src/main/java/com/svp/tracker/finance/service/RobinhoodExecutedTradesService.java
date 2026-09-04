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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticSyncedOrderRepository syncedOrderRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;

    @Transactional(readOnly = true)
    public RobinhoodExecutedTradesDto build(int year) {
        long ownerUserId = currentUser.requireUserId();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<RobinhoodExecutedTradeDto> trades = new ArrayList<>();
        Set<String> suffixes = new LinkedHashSet<>();
        for (RobinhoodAgenticSyncedOrder order :
                syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(ownerUserId)) {
            if (!isExecutedTrade(order.getState())) {
                continue;
            }
            String suffix = lastFour(order.getAccountNumber());
            if (suffix == null || !accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, suffix)) {
                continue;
            }
            Instant executedAt = order.getUpdatedAtRh() != null ? order.getUpdatedAtRh() : order.getCreatedAtRh();
            if (executedAt == null) {
                continue;
            }
            LocalDate tradeDate = executedAt.atZone(CENTRAL).toLocalDate();
            if (tradeDate.isBefore(yearStart) || tradeDate.isAfter(yearEnd)) {
                continue;
            }
            suffixes.add(suffix);
            BigDecimal qty = order.getQuantity();
            BigDecimal price = order.getAveragePrice() != null ? order.getAveragePrice() : order.getLimitPrice();
            trades.add(new RobinhoodExecutedTradeDto(
                    order.getSymbol(),
                    order.getSide(),
                    order.getOrderType(),
                    qty,
                    scaleMoney(price),
                    notional(order.getSymbol(), qty, price),
                    order.getState(),
                    executedAt,
                    suffix,
                    RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix)));
        }
        trades.sort(Comparator.comparing(
                RobinhoodExecutedTradeDto::executedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<RobinhoodRhPeriodAccountColumnDto> accounts = orderSuffixes(suffixes).stream()
                .map(s -> new RobinhoodRhPeriodAccountColumnDto(
                        s, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(s)))
                .toList();

        String note = trades.isEmpty()
                ? "No filled buys or sells in " + year + " yet. Sync Robinhood orders from Finance if this looks short."
                : "Filled and partially filled orders only, newest first. Times are America/Chicago.";
        return new RobinhoodExecutedTradesDto(year, note, accounts, trades);
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

    private static BigDecimal scaleMoney(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

}
