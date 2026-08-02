package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodOwnershipContractDto;
import com.svp.tracker.finance.dto.RobinhoodOwnershipContractSeriesDto;
import com.svp.tracker.finance.dto.RobinhoodOwnershipHistoryDto;
import com.svp.tracker.finance.dto.RobinhoodOwnershipHistoryPointDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Builds equity share-count history and option contract history from Daily Tracker {@code holdings_json}.
 * Fresh points arrive whenever the RH daily snapshot scheduler (hourly + 9 PM close) captures accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodOwnershipHistoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** Monkey / Then-Now capital start — option ownership history begins here. */
    static final LocalDate OPTIONS_HISTORY_START = LocalDate.of(2026, 6, 28);

    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public RobinhoodOwnershipHistoryDto build(
            String assetKindRaw,
            String symbolRaw,
            String contractKeyRaw,
            int year,
            String accountSuffixRaw,
            String captureKindRaw) {
        long ownerUserId = currentUser.requireUserId();
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year out of range");
        }

        String assetKind = normalizeAssetKind(assetKindRaw);
        String captureKind = normalizeCaptureKind(captureKindRaw);
        RobinhoodAccountTrackerConfig config = accountTrackerConfigService.getOrCreateConfig(ownerUserId);

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate from = "option".equals(assetKind) ? maxDate(yearStart, OPTIONS_HISTORY_START) : yearStart;
        if (from.isAfter(yearEnd)) {
            from = yearEnd;
        }

        List<RobinhoodRhDailySnapshot> rawYearRows = snapshotRepository
                .findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, from, yearEnd)
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

        if ("option".equals(assetKind)) {
            return buildOptions(accountRows, year, from, accountSuffix, captureKind, contractKeyRaw);
        }
        return buildEquity(accountRows, year, from, accountSuffix, captureKind, symbolRaw);
    }

    private RobinhoodOwnershipHistoryDto buildEquity(
            List<RobinhoodRhDailySnapshot> accountRows,
            int year,
            LocalDate from,
            String accountSuffix,
            String captureKind,
            String symbolRaw) {
        List<String> availableSymbols = collectEquitySymbols(accountRows);
        String symbol = resolveSymbol(symbolRaw, availableSymbols);

        List<RobinhoodOwnershipHistoryPointDto> points = new ArrayList<>();
        for (RobinhoodRhDailySnapshot row : accountRows) {
            HoldingAgg agg = extractEquity(row, symbol);
            points.add(toPoint(row, agg, true));
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

        Summary summary = summarize(points);
        RobinhoodOwnershipHistoryPointDto latest = points.isEmpty() ? null : points.get(points.size() - 1);

        return new RobinhoodOwnershipHistoryDto(
                "equity",
                symbol,
                null,
                null,
                accountSuffix,
                "••••" + accountSuffix,
                year,
                from,
                captureKind,
                availableSymbols,
                List.of(),
                availableSuffixesFrom(accountRows),
                summary.highDate,
                summary.highQty,
                summary.lowDate,
                summary.lowQty,
                latest == null ? ZERO : nullToZero(latest.quantity()),
                latest == null ? ZERO : nullToZero(latest.ownSharesEstimate()),
                latest == null ? ZERO : nullToZero(latest.marginSharesEstimate()),
                latest == null ? ZERO : nullToZero(latest.marginLoan()),
                latest == null ? null : latest.marketValue(),
                latest == null ? null : latest.costBasis(),
                points,
                List.of(),
                notes);
    }

    private RobinhoodOwnershipHistoryDto buildOptions(
            List<RobinhoodRhDailySnapshot> accountRows,
            int year,
            LocalDate from,
            String accountSuffix,
            String captureKind,
            String contractKeyRaw) {
        Map<String, List<DayHolding>> byContract = collectOptionDays(accountRows);
        List<RobinhoodOwnershipContractSeriesDto> allSeries = new ArrayList<>();
        List<RobinhoodOwnershipContractDto> availableContracts = new ArrayList<>();

        for (Map.Entry<String, List<DayHolding>> e : byContract.entrySet()) {
            List<DayHolding> days = e.getValue();
            days.sort(Comparator.comparing((DayHolding d) -> d.row.getSnapshotDate())
                    .thenComparing(d -> d.row.getSnapshotAt()));
            List<RobinhoodOwnershipHistoryPointDto> pts = new ArrayList<>();
            for (DayHolding d : days) {
                pts.add(toPoint(d.row, d.agg, false));
            }
            RobinhoodOwnershipContractDto meta = contractMeta(e.getKey(), days, pts);
            availableContracts.add(meta);
            allSeries.add(new RobinhoodOwnershipContractSeriesDto(meta, pts));
        }

        availableContracts.sort(Comparator.comparing(RobinhoodOwnershipContractDto::lastDate)
                .reversed()
                .thenComparing(RobinhoodOwnershipContractDto::label, String.CASE_INSENSITIVE_ORDER));
        allSeries.sort(Comparator.comparing((RobinhoodOwnershipContractSeriesDto s) -> s.contract().lastDate())
                .reversed()
                .thenComparing(s -> s.contract().label(), String.CASE_INSENSITIVE_ORDER));

        String contractKey = contractKeyRaw == null ? "" : contractKeyRaw.trim();
        List<String> notes = new ArrayList<>();
        notes.add(
                "Option contracts from Daily Tracker holdings, starting "
                        + OPTIONS_HISTORY_START
                        + ". Updates automatically with the hourly / 9 PM snapshot job.");
        notes.add(
                "New captures store strike, expiry, and call/put. Older snapshots without that metadata are labeled by chain + average buy price.");

        if (contractKey.isEmpty()) {
            notes.add("Showing all owned contracts in this range. Select a contract for a single-series chart.");
            if (availableContracts.isEmpty()) {
                notes.add("No option holdings found for account ••••" + accountSuffix + " since " + from + ".");
            }
            BigDecimal latestQty = availableContracts.stream()
                    .map(c -> nullToZero(c.latestQuantity()))
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal latestMv = availableContracts.stream()
                    .map(c -> nullToZero(c.latestMarketValue()))
                    .reduce(ZERO, BigDecimal::add);
            LocalDate highDate = null;
            BigDecimal highQty = null;
            for (RobinhoodOwnershipContractDto c : availableContracts) {
                if (c.highQuantity() == null) {
                    continue;
                }
                if (highQty == null || c.highQuantity().compareTo(highQty) > 0) {
                    highQty = c.highQuantity();
                    highDate = c.highDate();
                }
            }
            return new RobinhoodOwnershipHistoryDto(
                    "option",
                    null,
                    null,
                    "All contracts",
                    accountSuffix,
                    "••••" + accountSuffix,
                    year,
                    from,
                    captureKind,
                    List.of(),
                    availableContracts,
                    availableSuffixesFrom(accountRows),
                    highDate,
                    highQty,
                    null,
                    null,
                    latestQty,
                    ZERO,
                    ZERO,
                    ZERO,
                    latestMv,
                    null,
                    List.of(),
                    allSeries,
                    notes);
        }

        RobinhoodOwnershipContractSeriesDto selected = allSeries.stream()
                .filter(s -> contractKey.equals(s.contract().contractKey()))
                .findFirst()
                .orElse(null);
        List<RobinhoodOwnershipHistoryPointDto> points =
                selected == null ? List.of() : selected.points();
        RobinhoodOwnershipContractDto meta = selected == null ? null : selected.contract();
        if (selected == null) {
            notes.add("Contract key not found in this account/range — series is empty.");
        }

        Summary summary = summarize(points);
        RobinhoodOwnershipHistoryPointDto latest = points.isEmpty() ? null : points.get(points.size() - 1);

        return new RobinhoodOwnershipHistoryDto(
                "option",
                meta == null ? null : meta.chainSymbol(),
                meta == null ? contractKey : meta.contractKey(),
                meta == null ? contractKey : meta.label(),
                accountSuffix,
                "••••" + accountSuffix,
                year,
                from,
                captureKind,
                List.of(),
                availableContracts,
                availableSuffixesFrom(accountRows),
                summary.highDate,
                summary.highQty,
                summary.lowDate,
                summary.lowQty,
                latest == null ? ZERO : nullToZero(latest.quantity()),
                ZERO,
                ZERO,
                latest == null ? ZERO : nullToZero(latest.marginLoan()),
                latest == null ? null : latest.marketValue(),
                latest == null ? null : latest.costBasis(),
                points,
                List.of(),
                notes);
    }

    private Map<String, List<DayHolding>> collectOptionDays(List<RobinhoodRhDailySnapshot> rows) {
        Map<String, List<DayHolding>> byContract = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : rows) {
            Map<String, HoldingAgg> dayAggs = new LinkedHashMap<>();
            Map<String, RobinhoodRhHoldingDto> sample = new LinkedHashMap<>();
            for (RobinhoodRhHoldingDto h : readHoldings(row)) {
                if (!RobinhoodRhContractKeys.isOption(h)) {
                    continue;
                }
                String key = RobinhoodRhContractKeys.contractKeyForHolding(h);
                if (key.isEmpty()) {
                    continue;
                }
                HoldingAgg prev = dayAggs.getOrDefault(key, HoldingAgg.empty());
                dayAggs.put(key, prev.add(h));
                sample.putIfAbsent(key, h);
            }
            for (Map.Entry<String, HoldingAgg> e : dayAggs.entrySet()) {
                byContract
                        .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                        .add(new DayHolding(row, e.getValue(), sample.get(e.getKey())));
            }
        }
        return byContract;
    }

    private RobinhoodOwnershipContractDto contractMeta(
            String key, List<DayHolding> days, List<RobinhoodOwnershipHistoryPointDto> points) {
        DayHolding last = days.get(days.size() - 1);
        DayHolding first = days.get(0);
        RobinhoodRhHoldingDto sample = last.sample != null ? last.sample : first.sample;
        Summary summary = summarize(points);
        RobinhoodOwnershipHistoryPointDto latestPt = points.isEmpty() ? null : points.get(points.size() - 1);
        return new RobinhoodOwnershipContractDto(
                key,
                RobinhoodRhContractKeys.contractLabel(sample),
                RobinhoodRhContractKeys.chainSymbol(sample),
                RobinhoodRhContractKeys.optionType(sample),
                RobinhoodRhContractKeys.strikePrice(sample),
                RobinhoodRhContractKeys.expirationDate(sample),
                RobinhoodRhContractKeys.isLegacyIdentity(sample),
                first.row.getSnapshotDate(),
                last.row.getSnapshotDate(),
                latestPt == null ? ZERO : nullToZero(latestPt.quantity()),
                latestPt == null ? ZERO : nullToZero(latestPt.marketValue()),
                summary.highQty,
                summary.highDate);
    }

    private static List<String> availableSuffixesFrom(List<RobinhoodRhDailySnapshot> rows) {
        return rows.stream()
                .map(RobinhoodRhDailySnapshot::getAccountSuffix)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
                .stream()
                .toList();
    }

    private static String normalizeAssetKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return "equity";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("equity".equals(v) || "stock".equals(v) || "stocks".equals(v)) {
            return "equity";
        }
        if ("option".equals(v) || "options".equals(v) || "contract".equals(v) || "contracts".equals(v)) {
            return "option";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assetKind must be equity or option");
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
        HoldingAgg agg = HoldingAgg.empty();
        for (RobinhoodRhHoldingDto h : readHoldings(row)) {
            if (!isEquity(h)) {
                continue;
            }
            if (!symbol.equals(trimUpper(h.symbol()))) {
                continue;
            }
            agg = agg.add(h);
        }
        return agg;
    }

    private RobinhoodOwnershipHistoryPointDto toPoint(
            RobinhoodRhDailySnapshot row, HoldingAgg agg, boolean estimateOwnMargin) {
        BigDecimal cash = nullToZero(row.getCashBalance());
        BigDecimal equityMv = nullToZero(row.getEquityMarketValue());
        BigDecimal marginLoan = cash.signum() < 0 ? cash.abs() : ZERO;
        BigDecimal own;
        BigDecimal marginShares;
        if (estimateOwnMargin && agg.qty.signum() > 0 && equityMv.signum() > 0) {
            BigDecimal frac = marginLoan.divide(equityMv, 8, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            marginShares = agg.qty.multiply(frac).setScale(6, RoundingMode.HALF_UP);
            own = agg.qty.subtract(marginShares).setScale(6, RoundingMode.HALF_UP);
        } else {
            own = estimateOwnMargin ? agg.qty : ZERO;
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

    private static Summary summarize(List<RobinhoodOwnershipHistoryPointDto> points) {
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
        return new Summary(highDate, highQty, lowDate, lowQty);
    }

    private static boolean isEquity(RobinhoodRhHoldingDto h) {
        String type = h.positionType() == null ? "equity" : h.positionType().trim().toLowerCase(Locale.ROOT);
        return type.isEmpty() || "equity".equals(type) || "stock".equals(type);
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
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

    private record DayHolding(RobinhoodRhDailySnapshot row, HoldingAgg agg, RobinhoodRhHoldingDto sample) {}

    private record Summary(LocalDate highDate, BigDecimal highQty, LocalDate lowDate, BigDecimal lowQty) {}

    private record HoldingAgg(
            BigDecimal qty,
            BigDecimal mv,
            BigDecimal cost,
            BigDecimal pnl,
            BigDecimal avg,
            BigDecimal px) {
        static HoldingAgg empty() {
            return new HoldingAgg(ZERO, ZERO, ZERO, ZERO, null, null);
        }

        HoldingAgg add(RobinhoodRhHoldingDto h) {
            BigDecimal nextAvg = h.averageBuyPrice() != null ? h.averageBuyPrice() : avg;
            BigDecimal nextPx = h.currentUnitPrice() != null ? h.currentUnitPrice() : px;
            return new HoldingAgg(
                    qty.add(nullToZero(h.quantity())),
                    mv.add(nullToZero(h.marketValue())),
                    cost.add(nullToZero(h.costBasis())),
                    pnl.add(nullToZero(h.unrealizedPnL())),
                    nextAvg,
                    nextPx);
        }
    }
}
