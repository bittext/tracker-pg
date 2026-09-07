package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodRhCryptoSnapshot;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingPortfolioDto;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoCaptureResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerAccountCellDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerCaptureDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerReportDto;
import com.svp.tracker.finance.repository.RobinhoodRhCryptoSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Crypto holdings timeline, maintained like Daily Tracker (hourly + 9 PM CT close). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhCryptoTrackerService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter MANUAL_TIME = DateTimeFormatter.ofPattern("h:mm a")
            .withZone(CENTRAL)
            .withLocale(Locale.US);
    private static final List<String> COLUMN_ORDER = List.of("3370", "3550", "4123", "8696");

    public static final String STATUS_READY = "READY";
    public static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";

    private final CurrentUserService currentUser;
    private final AppUserRepository appUserRepository;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhCryptoTrackerProperties cryptoTrackerProps;
    private final RobinhoodCryptoTradingService cryptoTradingService;
    private final RobinhoodRhCryptoSnapshotRepository snapshotRepository;
    private final ObjectProvider<RobinhoodRhCryptoTrackerService> selfProvider;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public RobinhoodRhCryptoTrackerReportDto buildReport(int year, List<Integer> months) {
        long ownerUserId = currentUser.requireUserId();
        boolean sidecarConfigured = agenticProps.serviceConfigured();
        boolean cryptoConnected = cryptoTradingService.isConnected(ownerUserId);
        int snapshotCount = (int) snapshotRepository.countByOwnerUserId(ownerUserId);

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        Set<Integer> monthFilter = months == null || months.isEmpty() ? null : new HashSet<>(months);

        List<RobinhoodRhCryptoSnapshot> rows =
                snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescSnapshotAtDesc(
                        ownerUserId, yearStart, yearEnd);
        rows = rows.stream().filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter)).toList();

        List<RobinhoodRhCryptoTrackerDayDto> days = buildDays(rows);
        List<RobinhoodRhCryptoTrackerAccountColumnDto> columns = accountColumns(days);
        String status = cryptoConnected ? STATUS_READY : STATUS_NOT_CONNECTED;
        List<String> notes = buildNotes(sidecarConfigured, cryptoConnected, snapshotCount, cryptoTrackerProps);

        return new RobinhoodRhCryptoTrackerReportDto(
                year,
                months == null || months.isEmpty() ? List.of() : List.copyOf(months),
                status,
                sidecarConfigured,
                cryptoConnected,
                sidecarConfigured && cryptoConnected,
                snapshotCount,
                cryptoTrackerProps.autoCaptureScheduleLabel(),
                columns,
                days,
                notes);
    }

    public RobinhoodRhCryptoCaptureResultDto captureNow(boolean syncLatest) {
        long ownerUserId = currentUser.requireUserId();
        if (!cryptoTradingService.isConnected(ownerUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Connect Robinhood Crypto Trading API credentials first.");
        }
        return selfProvider
                .getObject()
                .captureForOwner(ownerUserId, Instant.now(), RobinhoodRhDailyCaptureKind.MANUAL, syncLatest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public RobinhoodRhCryptoCaptureResultDto captureScheduledForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.SCHEDULED, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public RobinhoodRhCryptoCaptureResultDto captureIntradayForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.INTRADAY, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ResponseStatusException.class)
    public RobinhoodRhCryptoCaptureResultDto captureForOwner(
            long ownerUserId, Instant snapshotAt, String captureKind, boolean syncLatest) {
        if (!cryptoTradingService.isConnected(ownerUserId)) {
            return new RobinhoodRhCryptoCaptureResultDto(
                    false, snapshotAt, "Crypto Trading API not connected.", 0);
        }

        RobinhoodCryptoTradingSyncResultDto syncResult;
        try {
            if (syncLatest) {
                syncResult = cryptoTradingService.syncForOwner(ownerUserId);
            } else {
                syncResult = cryptoTradingService
                        .cachedSyncResult(ownerUserId)
                        .orElseGet(() -> cryptoTradingService.syncForOwner(ownerUserId));
            }
        } catch (ResponseStatusException e) {
            return new RobinhoodRhCryptoCaptureResultDto(false, snapshotAt, e.getReason(), 0);
        }

        if (!syncResult.ok()) {
            return new RobinhoodRhCryptoCaptureResultDto(false, snapshotAt, syncResult.message(), 0);
        }

        String username = appUserRepository.findById(ownerUserId).map(u -> u.getUsername()).orElse("");
        List<RobinhoodCryptoTradingPortfolioDto> portfolios = portfoliosOf(syncResult);
        LocalDate snapshotDate = snapshotAt.atZone(CENTRAL).toLocalDate();
        Instant now = Instant.now();
        boolean pointInTime = isPointInTimeCaptureKind(captureKind);
        int captured = 0;
        int coinCount = 0;

        for (RobinhoodCryptoTradingPortfolioDto portfolio : portfolios) {
            String suffix = RobinhoodRhCryptoAccountPolicy.suffixForCryptoAccount(portfolio.accountNumber());
            if (!RobinhoodRhCryptoAccountPolicy.includeForUser(username, suffix)) {
                continue;
            }
            List<RobinhoodRhCryptoHoldingDto> holdings =
                    portfolio.holdings() == null ? List.of() : portfolio.holdings();
            BigDecimal total = portfolio.totalValue() == null ? BigDecimal.ZERO : portfolio.totalValue();

            RobinhoodRhCryptoSnapshot snapshot = pointInTime
                    ? new RobinhoodRhCryptoSnapshot()
                    : snapshotRepository
                            .findByOwnerUserIdAndSnapshotDateAndAccountSuffixAndCaptureKind(
                                    ownerUserId, snapshotDate, suffix, RobinhoodRhDailyCaptureKind.SCHEDULED)
                            .orElseGet(RobinhoodRhCryptoSnapshot::new);
            snapshot.setOwnerUserId(ownerUserId);
            snapshot.setSnapshotAt(snapshotAt);
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setCaptureKind(captureKind);
            snapshot.setAccountSuffix(suffix);
            snapshot.setAccountNumber(maskAccount(portfolio.accountNumber()));
            snapshot.setLabel(RobinhoodRhCryptoAccountPolicy.labelForSuffix(suffix).orElse("Account"));
            snapshot.setTotalValue(scaleMoney(total));
            snapshot.setHoldingsJson(writeJson(holdings));
            if (snapshot.getCreatedAt() == null) {
                snapshot.setCreatedAt(now);
            }
            snapshotRepository.save(snapshot);
            captured++;
            coinCount += holdings.size();
        }

        if (captured == 0) {
            return new RobinhoodRhCryptoCaptureResultDto(
                    false, snapshotAt, "No Daily Tracker crypto accounts were visible on this key.", 0);
        }

        String message;
        if (RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind)) {
            message = "Saved manual crypto capture at "
                    + MANUAL_TIME.format(snapshotAt)
                    + " Central ("
                    + captured
                    + " account"
                    + (captured == 1 ? "" : "s")
                    + "). The daily 9 PM CT row is unchanged.";
        } else if (RobinhoodRhDailyCaptureKind.INTRADAY.equals(captureKind)) {
            message = "Saved hourly crypto capture at "
                    + MANUAL_TIME.format(snapshotAt)
                    + " Central ("
                    + captured
                    + " account"
                    + (captured == 1 ? "" : "s")
                    + ").";
        } else {
            message = "Captured "
                    + captured
                    + " scheduled 9 PM CT crypto snapshot(s) for "
                    + snapshotDate
                    + " (Central date).";
        }
        return new RobinhoodRhCryptoCaptureResultDto(true, snapshotAt, message, coinCount);
    }

    private List<RobinhoodCryptoTradingPortfolioDto> portfoliosOf(RobinhoodCryptoTradingSyncResultDto syncResult) {
        if (syncResult.portfolios() != null && !syncResult.portfolios().isEmpty()) {
            return syncResult.portfolios();
        }
        return List.of(new RobinhoodCryptoTradingPortfolioDto(
                syncResult.accountNumber(), syncResult.totalValue(), syncResult.holdings()));
    }

    private List<RobinhoodRhCryptoTrackerDayDto> buildDays(List<RobinhoodRhCryptoSnapshot> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<RobinhoodRhCryptoSnapshot>> byDate = new TreeMap<>();
        for (RobinhoodRhCryptoSnapshot row : rows) {
            if (row.getSnapshotDate() == null) {
                continue;
            }
            byDate.computeIfAbsent(row.getSnapshotDate(), ignored -> new ArrayList<>()).add(row);
        }

        List<RobinhoodRhCryptoTrackerDayDto> chronological = new ArrayList<>();
        BigDecimal priorOfficial = null;
        for (Map.Entry<LocalDate, List<RobinhoodRhCryptoSnapshot>> entry : byDate.entrySet()) {
            List<RobinhoodRhCryptoSnapshot> dayRows = entry.getValue();
            Map<String, RobinhoodRhCryptoSnapshot> officialBySuffix = latestBySuffix(dayRows, true);
            if (officialBySuffix.isEmpty()) {
                officialBySuffix = latestBySuffix(dayRows, false);
            }
            List<RobinhoodRhCryptoTrackerAccountCellDto> accounts = new ArrayList<>();
            List<RobinhoodRhCryptoHoldingDto> combinedHoldings = new ArrayList<>();
            BigDecimal combined = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Instant officialAt = null;
            String officialKind = RobinhoodRhDailyCaptureKind.SCHEDULED;
            for (RobinhoodRhCryptoSnapshot row : officialBySuffix.values().stream()
                    .sorted(Comparator.comparing(r -> columnRank(r.getAccountSuffix())))
                    .toList()) {
                List<RobinhoodRhCryptoHoldingDto> holdings = readHoldings(row.getHoldingsJson());
                combinedHoldings.addAll(holdings);
                BigDecimal total = nullToZero(row.getTotalValue());
                combined = combined.add(total);
                if (officialAt == null || row.getSnapshotAt() != null && row.getSnapshotAt().isAfter(officialAt)) {
                    officialAt = row.getSnapshotAt();
                    officialKind = row.getCaptureKind();
                }
                accounts.add(new RobinhoodRhCryptoTrackerAccountCellDto(
                        blankToEmpty(row.getAccountSuffix()),
                        row.getLabel() == null || row.getLabel().isBlank()
                                ? RobinhoodRhCryptoAccountPolicy.labelForSuffix(row.getAccountSuffix())
                                        .orElse("Account")
                                : row.getLabel(),
                        scaleMoney(total),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        holdings));
            }
            BigDecimal change = priorOfficial == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : scaleMoney(combined.subtract(priorOfficial));
            priorOfficial = combined;
            chronological.add(new RobinhoodRhCryptoTrackerDayDto(
                    entry.getKey(),
                    officialAt,
                    officialKind,
                    scaleMoney(combined),
                    change,
                    List.copyOf(combinedHoldings),
                    List.copyOf(accounts),
                    capturesOfKind(dayRows, RobinhoodRhDailyCaptureKind.INTRADAY),
                    capturesOfKind(dayRows, RobinhoodRhDailyCaptureKind.MANUAL)));
        }
        chronological.sort(Comparator.comparing(RobinhoodRhCryptoTrackerDayDto::snapshotDate).reversed());
        return chronological;
    }

    private Map<String, RobinhoodRhCryptoSnapshot> latestBySuffix(
            List<RobinhoodRhCryptoSnapshot> dayRows, boolean scheduledOnly) {
        Map<String, RobinhoodRhCryptoSnapshot> latest = new LinkedHashMap<>();
        for (RobinhoodRhCryptoSnapshot row : dayRows) {
            if (scheduledOnly && !RobinhoodRhDailyCaptureKind.SCHEDULED.equals(row.getCaptureKind())) {
                continue;
            }
            String suffix = blankToEmpty(row.getAccountSuffix());
            RobinhoodRhCryptoSnapshot existing = latest.get(suffix);
            if (existing == null
                    || row.getSnapshotAt() != null
                            && (existing.getSnapshotAt() == null || row.getSnapshotAt().isAfter(existing.getSnapshotAt()))) {
                latest.put(suffix, row);
            }
        }
        return latest;
    }

    private List<RobinhoodRhCryptoTrackerCaptureDto> capturesOfKind(
            List<RobinhoodRhCryptoSnapshot> dayRows, String kind) {
        Map<Instant, List<RobinhoodRhCryptoSnapshot>> byInstant = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhCryptoSnapshot row : dayRows) {
            if (!kind.equals(row.getCaptureKind()) || row.getSnapshotAt() == null) {
                continue;
            }
            byInstant.computeIfAbsent(row.getSnapshotAt(), ignored -> new ArrayList<>()).add(row);
        }
        List<RobinhoodRhCryptoTrackerCaptureDto> out = new ArrayList<>();
        for (Map.Entry<Instant, List<RobinhoodRhCryptoSnapshot>> entry : byInstant.entrySet()) {
            BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            List<RobinhoodRhCryptoTrackerAccountCellDto> accounts = new ArrayList<>();
            for (RobinhoodRhCryptoSnapshot row : entry.getValue().stream()
                    .sorted(Comparator.comparing(r -> columnRank(r.getAccountSuffix())))
                    .toList()) {
                BigDecimal value = nullToZero(row.getTotalValue());
                total = total.add(value);
                accounts.add(new RobinhoodRhCryptoTrackerAccountCellDto(
                        blankToEmpty(row.getAccountSuffix()),
                        row.getLabel() == null || row.getLabel().isBlank()
                                ? RobinhoodRhCryptoAccountPolicy.labelForSuffix(row.getAccountSuffix())
                                        .orElse("Account")
                                : row.getLabel(),
                        scaleMoney(value),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        readHoldings(row.getHoldingsJson())));
            }
            out.add(new RobinhoodRhCryptoTrackerCaptureDto(entry.getKey(), kind, scaleMoney(total), accounts));
        }
        return List.copyOf(out);
    }

    private List<RobinhoodRhCryptoTrackerAccountColumnDto> accountColumns(List<RobinhoodRhCryptoTrackerDayDto> days) {
        Set<String> seen = new HashSet<>();
        List<RobinhoodRhCryptoTrackerAccountColumnDto> out = new ArrayList<>();
        for (String suffix : COLUMN_ORDER) {
            if (days.stream().anyMatch(d -> d.accounts().stream().anyMatch(a -> suffix.equals(a.accountSuffix())))) {
                seen.add(suffix);
                out.add(new RobinhoodRhCryptoTrackerAccountColumnDto(
                        suffix, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix)));
            }
        }
        for (RobinhoodRhCryptoTrackerDayDto day : days) {
            for (RobinhoodRhCryptoTrackerAccountCellDto cell : day.accounts()) {
                if (seen.add(cell.accountSuffix())) {
                    out.add(new RobinhoodRhCryptoTrackerAccountColumnDto(cell.accountSuffix(), cell.label()));
                }
            }
        }
        return List.copyOf(out);
    }

    private static int columnRank(String suffix) {
        int idx = COLUMN_ORDER.indexOf(suffix == null ? "" : suffix);
        return idx < 0 ? 100 : idx;
    }

    private List<RobinhoodRhCryptoHoldingDto> readHoldings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RobinhoodRhCryptoHoldingDto> rows = objectMapper.readValue(json, new TypeReference<>() {});
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean matchesMonthFilter(LocalDate date, Set<Integer> monthFilter) {
        if (monthFilter == null || monthFilter.isEmpty()) {
            return true;
        }
        return monthFilter.contains(date.getMonthValue());
    }

    private static List<String> buildNotes(
            boolean sidecarConfigured,
            boolean cryptoConnected,
            int snapshotCount,
            RobinhoodRhCryptoTrackerProperties props) {
        List<String> notes = new ArrayList<>();
        notes.add(
                "Same cadence as Daily Tracker: hourly captures and a frozen 9 PM CT close for Individual ••••3370, "
                        + "Agentic ••••3550, and Ammu ••••8696. Own-account crypto books only — ••••0440 and ••••2835 stay out.");
        if (!sidecarConfigured) {
            notes.add("Robinhood sidecar is not configured on this server.");
        } else if (!cryptoConnected) {
            notes.add(
                    "Add Crypto Trading API credentials below (create keys in Robinhood crypto account settings on web).");
        } else if (snapshotCount == 0) {
            notes.add("Connected. Use Capture now or wait for the hourly job; the official day row writes at 9 PM CT.");
        }
        if (props.snapshotSchedulerActive()) {
            notes.add("Auto-capture: " + props.autoCaptureScheduleLabel() + ".");
        }
        notes.add("Capture now does not replace that day's 9 PM CT close.");
        notes.add(
                "Average, cost, and buy fees come from filled crypto orders (FIFO, fees included). "
                        + "Sell-all brokerage uses the last exchange taker rate, usually 0.95%.");
        return List.copyOf(notes);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean isPointInTimeCaptureKind(String captureKind) {
        return RobinhoodRhDailyCaptureKind.INTRADAY.equals(captureKind)
                || RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind);
    }

    private static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber == null ? "" : accountNumber;
        }
        return "••••" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return nullToZero(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v;
    }
}
