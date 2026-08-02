package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodOwnershipHistoryDto;
import com.svp.tracker.finance.dto.RobinhoodOwnershipHistoryPointDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a per-symbol share-count history from Daily Tracker {@code holdings_json}. Fresh points arrive
 * automatically whenever the existing RH daily snapshot scheduler (hourly + 9 PM close) captures accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodOwnershipHistoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RobinhoodOwnershipHistoryDto build(
            String symbolRaw, int year, String accountSuffixRaw, String captureKindRaw) {
        long ownerUserId = currentUser.requireUserId();
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year out of range");
        }

        String captureKind = normalizeCaptureKind(captureKindRaw);
        RobinhoodAccountTrackerConfig config = accountTrackerConfigService.getOrCreateConfig(ownerUserId);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<RobinhoodRhDailySnapshot> rawYearRows = snapshotRepository
                .findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, from, to)
                .stream()
                .filter(r -> captureKind.equals(r.getCaptureKind()))
                .toList();
        Set<String> allowedSuffixes = new TreeSet<>();
        for (RobinhoodRhDailySnapshot row : rawYearRows) {
            String suf = row.getAccountSuffix();
            if (suf != null
                    && !suf.isBlank()
                    && accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, suf)) {
                allowedSuffixes.add(suf.trim());
            }
        }
        List<RobinhoodRhDailySnapshot> yearRows = rawYearRows.stream()
                .filter(r -> r.getAccountSuffix() != null && allowedSuffixes.contains(r.getAccountSuffix().trim()))
                .toList();

        List<String> availableSuffixes = yearRows.stream()
                .map(RobinhoodRhDailySnapshot::getAccountSuffix)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
                .stream()
                .toList();

        String accountSuffix = resolveSuffix(accountSuffixRaw, config, availableSuffixes);
        List<RobinhoodRhDailySnapshot> accountRows = yearRows.stream()
                .filter(r -> accountSuffix.equals(r.getAccountSuffix()))
                .sorted(Comparator.comparing(RobinhoodRhDailySnapshot::getSnapshotDate)
                        .thenComparing(RobinhoodRhDailySnapshot::getSnapshotAt))
                .toList();

        List<String> availableSymbols = collectEquitySymbols(accountRows);
        String symbol = resolveSymbol(symbolRaw, availableSymbols);

        List<RobinhoodOwnershipHistoryPointDto> points = new ArrayList<>();
        for (RobinhoodRhDailySnapshot row : accountRows) {
            HoldingAgg agg = extractEquity(row, symbol);
            points.add(toPoint(row, agg));
        }

        List<String> notes = new ArrayList<>();
        notes.add(
                "Updated automatically by the Daily Tracker snapshot job (hourly intraday + 9 PM Eastern scheduled close).");
        notes.add(
                "Own vs margin share split estimates margin loan as max(0, −cash) and attributes shares by loan ÷ equity market value.");
        if (points.isEmpty()) {
            notes.add("No " + captureKind + " snapshots for account ••••" + accountSuffix + " in " + year + ".");
        }
        if (!availableSymbols.contains(symbol) && !points.isEmpty()) {
            notes.add("Symbol " + symbol + " was not found in holdings for this account/year — series may be zeros.");
        }

        LocalDate highDate = null;
        LocalDate lowDate = null;
        BigDecimal highQty = null;
        BigDecimal lowQty = null;
        for (RobinhoodOwnershipHistoryPointDto p : points) {
            BigDecimal q = nullToZero(p.quantity());
            if (highQty == null || q.compareTo(highQty) > 0) {
                highQty = q;
                highDate = p.snapshotDate();
            }
            if (q.compareTo(ZERO) > 0 && (lowQty == null || q.compareTo(lowQty) < 0)) {
                lowQty = q;
                lowDate = p.snapshotDate();
            }
        }

        RobinhoodOwnershipHistoryPointDto latest = points.isEmpty() ? null : points.get(points.size() - 1);

        return new RobinhoodOwnershipHistoryDto(
                symbol,
                accountSuffix,
                "••••" + accountSuffix,
                year,
                captureKind,
                availableSymbols,
                availableSuffixes,
                highDate,
                highQty,
                lowDate,
                lowQty,
                latest == null ? ZERO : nullToZero(latest.quantity()),
                latest == null ? ZERO : nullToZero(latest.ownSharesEstimate()),
                latest == null ? ZERO : nullToZero(latest.marginSharesEstimate()),
                latest == null ? ZERO : nullToZero(latest.marginLoan()),
                latest == null ? null : latest.marketValue(),
                latest == null ? null : latest.costBasis(),
                points,
                notes);
    }

    private static String normalizeCaptureKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return RobinhoodRhDailyCaptureKind.SCHEDULED;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (RobinhoodRhDailyCaptureKind.SCHEDULED.equals(u)
                || RobinhoodRhDailyCaptureKind.INTRADAY.equals(u)
                || RobinhoodRhDailyCaptureKind.MANUAL.equals(u)) {
            return u;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "captureKind must be SCHEDULED, INTRADAY, or MANUAL");
    }

    private static String resolveSuffix(
            String raw, RobinhoodAccountTrackerConfig config, List<String> available) {
        if (raw != null && !raw.isBlank()) {
            String s = raw.trim();
            if (s.startsWith("••••")) {
                s = s.substring(4);
            }
            return s;
        }
        if (config.getIndividualAccountSuffix() != null && !config.getIndividualAccountSuffix().isBlank()) {
            return config.getIndividualAccountSuffix().trim();
        }
        if (!available.isEmpty()) {
            return available.get(0);
        }
        return "3370";
    }

    private static String resolveSymbol(String raw, List<String> available) {
        if (raw != null && !raw.isBlank()) {
            return raw.trim().toUpperCase(Locale.ROOT);
        }
        if (available.contains("NBIS")) {
            return "NBIS";
        }
        if (!available.isEmpty()) {
            return available.get(0);
        }
        return "NBIS";
    }

    private List<String> collectEquitySymbols(List<RobinhoodRhDailySnapshot> rows) {
        Set<String> symbols = new TreeSet<>();
        for (RobinhoodRhDailySnapshot row : rows) {
            for (RobinhoodRhHoldingDto h : readHoldings(row)) {
                if (!isEquity(h)) {
                    continue;
                }
                String sym = trimUpper(h.symbol());
                if (!sym.isEmpty()) {
                    symbols.add(sym);
                }
            }
        }
        return List.copyOf(symbols);
    }

    private HoldingAgg extractEquity(RobinhoodRhDailySnapshot row, String symbol) {
        BigDecimal qty = ZERO;
        BigDecimal mv = ZERO;
        BigDecimal cost = ZERO;
        BigDecimal pnl = ZERO;
        BigDecimal avg = null;
        BigDecimal px = null;
        for (RobinhoodRhHoldingDto h : readHoldings(row)) {
            if (!isEquity(h)) {
                continue;
            }
            if (!symbol.equals(trimUpper(h.symbol()))) {
                continue;
            }
            qty = qty.add(nullToZero(h.quantity()));
            mv = mv.add(nullToZero(h.marketValue()));
            cost = cost.add(nullToZero(h.costBasis()));
            pnl = pnl.add(nullToZero(h.unrealizedPnL()));
            if (h.averageBuyPrice() != null) {
                avg = h.averageBuyPrice();
            }
            if (h.currentUnitPrice() != null) {
                px = h.currentUnitPrice();
            }
        }
        return new HoldingAgg(qty, mv, cost, pnl, avg, px);
    }

    private RobinhoodOwnershipHistoryPointDto toPoint(RobinhoodRhDailySnapshot row, HoldingAgg agg) {
        BigDecimal cash = nullToZero(row.getCashBalance());
        BigDecimal equityMv = nullToZero(row.getEquityMarketValue());
        BigDecimal marginLoan = cash.signum() < 0 ? cash.abs() : ZERO;
        BigDecimal own;
        BigDecimal marginShares;
        if (agg.qty.signum() > 0 && equityMv.signum() > 0) {
            BigDecimal frac = marginLoan.divide(equityMv, 8, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            marginShares = agg.qty.multiply(frac).setScale(6, RoundingMode.HALF_UP);
            own = agg.qty.subtract(marginShares).setScale(6, RoundingMode.HALF_UP);
        } else {
            own = agg.qty;
            marginShares = ZERO;
        }
        return new RobinhoodOwnershipHistoryPointDto(
                row.getSnapshotDate(),
                row.getSnapshotAt(),
                row.getCaptureKind(),
                row.getId() == null ? 0L : row.getId(),
                scaleQty(agg.qty),
                scaleMoney(agg.mv),
                scaleUnit(agg.avg),
                scaleMoney(agg.cost),
                scaleMoney(agg.pnl),
                scaleUnit(agg.px),
                scaleMoney(cash),
                scaleMoney(equityMv),
                scaleMoney(nullToZero(row.getTotalAccountValue())),
                scaleMoney(marginLoan),
                own,
                marginShares);
    }

    private List<RobinhoodRhHoldingDto> readHoldings(RobinhoodRhDailySnapshot row) {
        try {
            List<RobinhoodRhHoldingDto> raw =
                    objectMapper.readValue(row.getHoldingsJson(), new TypeReference<>() {});
            return RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(raw);
        } catch (Exception e) {
            log.debug("Failed to parse holdings_json for snapshot {}: {}", row.getId(), e.getMessage());
            return List.of();
        }
    }

    private static boolean isEquity(RobinhoodRhHoldingDto h) {
        String type = h.positionType() == null ? "equity" : h.positionType().trim().toLowerCase(Locale.ROOT);
        return type.isEmpty() || "equity".equals(type) || "stock".equals(type);
    }

    private static String trimUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return nullToZero(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleQty(BigDecimal v) {
        return nullToZero(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleUnit(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP);
    }

    private record HoldingAgg(
            BigDecimal qty,
            BigDecimal mv,
            BigDecimal cost,
            BigDecimal pnl,
            BigDecimal avg,
            BigDecimal px) {}
}
