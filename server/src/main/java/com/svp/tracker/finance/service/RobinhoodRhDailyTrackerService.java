package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyCaptureResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailySnapshotDetailDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountCellDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Daily 9 PM Central Robinhood account snapshots for Reports → Daily Tracker. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhDailyTrackerService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    private final CurrentUserService currentUser;
    private final RobinhoodRhAccountsTrackService rhAccountsTrackService;
    private final RobinhoodAgenticService agenticService;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAgenticProperties agenticProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerReportDto buildReport(int year, Integer month) {
        long ownerUserId = currentUser.requireUserId();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<RobinhoodRhDailySnapshot> yearRows =
                snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, yearStart, yearEnd);

        LocalDate filterFrom = month != null ? LocalDate.of(year, month, 1) : yearStart;
        LocalDate filterTo = month != null ? YearMonth.of(year, month).atEndOfMonth() : yearEnd;

        List<RobinhoodRhDailySnapshot> rows = yearRows.stream()
                .filter(r -> !r.getSnapshotDate().isBefore(filterFrom) && !r.getSnapshotDate().isAfter(filterTo))
                .toList();

        LinkedHashSet<String> suffixOrder = new LinkedHashSet<>();
        Map<String, RobinhoodRhDailyTrackerAccountColumnDto> columnBySuffix = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : rows) {
            suffixOrder.add(row.getAccountSuffix());
            columnBySuffix.putIfAbsent(
                    row.getAccountSuffix(),
                    new RobinhoodRhDailyTrackerAccountColumnDto(
                            row.getAccountSuffix(), row.getLabel(), row.getAccountKind()));
        }

        Map<LocalDate, List<RobinhoodRhDailySnapshot>> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhDailySnapshot row : rows) {
            byDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }

        List<RobinhoodRhDailyTrackerDayDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<RobinhoodRhDailySnapshot>> entry : byDate.entrySet()) {
            List<RobinhoodRhDailySnapshot> dayRows = entry.getValue();
            dayRows.sort(Comparator.comparing(RobinhoodRhDailySnapshot::getAccountSuffix));
            Instant snapshotAt = dayRows.stream()
                    .map(RobinhoodRhDailySnapshot::getSnapshotAt)
                    .max(Instant::compareTo)
                    .orElse(null);

            BigDecimal combinedTotal = BigDecimal.ZERO;
            BigDecimal combinedAdded = BigDecimal.ZERO;
            BigDecimal combinedRemoved = BigDecimal.ZERO;
            BigDecimal combinedValueChange = BigDecimal.ZERO;
            List<RobinhoodRhDailyTrackerAccountCellDto> cells = new ArrayList<>();

            for (RobinhoodRhDailySnapshot row : dayRows) {
                combinedTotal = combinedTotal.add(nullToZero(row.getTotalAccountValue()));
                combinedAdded = combinedAdded.add(nullToZero(row.getPeriodAdded()));
                combinedRemoved = combinedRemoved.add(nullToZero(row.getPeriodRemoved()));
                combinedValueChange = combinedValueChange.add(nullToZero(row.getPeriodValueChange()));
                boolean flowActivity =
                        row.getPeriodAdded().signum() != 0 || row.getPeriodRemoved().signum() != 0;
                cells.add(new RobinhoodRhDailyTrackerAccountCellDto(
                        row.getId(),
                        row.getAccountSuffix(),
                        scaleMoney(row.getTotalAccountValue()),
                        scaleMoney(row.getPeriodAdded()),
                        scaleMoney(row.getPeriodRemoved()),
                        scaleMoney(row.getPeriodValueChange()),
                        flowActivity));
            }

            days.add(new RobinhoodRhDailyTrackerDayDto(
                    entry.getKey(),
                    snapshotAt,
                    scaleMoney(combinedTotal),
                    scaleMoney(combinedAdded),
                    scaleMoney(combinedRemoved),
                    scaleMoney(combinedValueChange),
                    cells));
        }

        List<RobinhoodRhDailySnapshot> monthRows = month != null
                ? yearRows.stream()
                        .filter(r -> r.getSnapshotDate().getMonthValue() == month)
                        .toList()
                : yearRows;

        BigDecimal monthCombinedTotal = latestCombinedTotal(monthRows);
        BigDecimal monthCombinedChange = combinedChange(monthRows);
        BigDecimal yearCombinedTotal = latestCombinedTotal(yearRows);
        BigDecimal yearCombinedChange = combinedChange(yearRows);

        List<String> notes = new ArrayList<>();
        notes.add("Snapshots taken daily at 9:00 PM Central. Period flows are cash movements since the previous 9 PM snapshot.");
        if (rows.isEmpty()) {
            notes.add("No snapshots yet — enable the daily snapshot scheduler or click Capture now after connecting Agentic Trading.");
        }

        return new RobinhoodRhDailyTrackerReportDto(
                year,
                month,
                monthCombinedTotal,
                monthCombinedChange,
                yearCombinedTotal,
                yearCombinedChange,
                suffixOrder.stream().map(columnBySuffix::get).filter(Objects::nonNull).toList(),
                days,
                notes);
    }

    @Transactional(readOnly = true)
    public RobinhoodRhDailySnapshotDetailDto getSnapshotDetail(long snapshotId) {
        long ownerUserId = currentUser.requireUserId();
        RobinhoodRhDailySnapshot row = snapshotRepository
                .findByIdAndOwnerUserId(snapshotId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        return toDetailDto(row);
    }

    @Transactional
    public RobinhoodRhDailyCaptureResultDto captureNow(boolean syncLatest) {
        long ownerUserId = currentUser.requireUserId();
        if (syncLatest && agenticProps.serviceConfigured() && agenticProps.enabled()) {
            connectionRepository.findByOwnerUserId(ownerUserId).ifPresent(agenticService::syncConnection);
        }
        return captureSnapshotsForOwner(ownerUserId, Instant.now());
    }

    /** Called by scheduled job — no HTTP user context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodRhDailyCaptureResultDto captureSnapshotsForOwner(long ownerUserId, Instant snapshotAt) {
        RobinhoodRhAccountsTrackDto track = rhAccountsTrackService.buildForOwner(ownerUserId, false);
        LocalDate snapshotDate = snapshotAt.atZone(CENTRAL).toLocalDate();
        Instant now = Instant.now();
        int captured = 0;

        for (RobinhoodRhAccountSummaryDto acct : track.accounts()) {
            String suffix = acct.accountSuffix();
            if (suffix == null || suffix.isBlank()) {
                continue;
            }

            Optional<RobinhoodRhDailySnapshot> prevOpt =
                    snapshotRepository.findTopByOwnerUserIdAndAccountSuffixAndSnapshotDateLessThanOrderBySnapshotDateDesc(
                            ownerUserId, suffix, snapshotDate);
            LocalDate periodStartDate = prevOpt.map(RobinhoodRhDailySnapshot::getSnapshotDate).orElse(null);
            BigDecimal prevTotal = prevOpt
                    .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                    .orElse(nullToZero(acct.startingTotalValue()));

            List<RobinhoodRhCashFlowEventDto> periodFlows =
                    flowsInPeriod(acct.cashFlowEvents(), periodStartDate, snapshotDate);
            BigDecimal periodAdded = sumFlowAmounts(periodFlows, true);
            BigDecimal periodRemoved = sumFlowAmounts(periodFlows, false);

            BigDecimal currentTotal = nullToZero(acct.totalAccountValue());
            BigDecimal valueChange =
                    currentTotal.subtract(prevTotal).subtract(periodAdded).add(periodRemoved);

            RobinhoodRhDailySnapshot snapshot = snapshotRepository
                    .findByOwnerUserIdAndSnapshotDateAndAccountSuffix(ownerUserId, snapshotDate, suffix)
                    .orElseGet(RobinhoodRhDailySnapshot::new);
            snapshot.setOwnerUserId(ownerUserId);
            snapshot.setSnapshotAt(snapshotAt);
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setAccountSuffix(suffix);
            snapshot.setAccountNumber(acct.accountNumberMasked());
            snapshot.setLabel(acct.label());
            snapshot.setAccountKind(acct.accountKind());
            snapshot.setTotalAccountValue(scaleMoney(currentTotal));
            snapshot.setCashBalance(scaleMoney(nullToZero(acct.cashBalance())));
            snapshot.setEquityMarketValue(scaleMoney(nullToZero(acct.equityMarketValue())));
            snapshot.setPeriodAdded(scaleMoney(periodAdded));
            snapshot.setPeriodRemoved(scaleMoney(periodRemoved));
            snapshot.setPeriodValueChange(scaleMoney(valueChange));
            snapshot.setPeriodStartDate(periodStartDate);
            snapshot.setHoldingsJson(writeJson(acct.holdings()));
            snapshot.setFlowsJson(writeJson(periodFlows));
            if (snapshot.getCreatedAt() == null) {
                snapshot.setCreatedAt(now);
            }
            snapshotRepository.save(snapshot);
            captured++;
        }

        String message = captured == 0
                ? "No accounts to snapshot — connect Agentic Trading and sync holdings first."
                : "Captured " + captured + " account snapshot(s) for " + snapshotDate + " Central.";
        log.info("RH daily snapshot for user {}: {}", ownerUserId, message);
        return new RobinhoodRhDailyCaptureResultDto(true, snapshotAt, captured, message);
    }

    private RobinhoodRhDailySnapshotDetailDto toDetailDto(RobinhoodRhDailySnapshot row) {
        List<RobinhoodRhHoldingDto> holdings = readJson(row.getHoldingsJson(), new TypeReference<>() {});
        List<RobinhoodRhCashFlowEventDto> flows = readJson(row.getFlowsJson(), new TypeReference<>() {});
        return new RobinhoodRhDailySnapshotDetailDto(
                row.getId(),
                row.getSnapshotDate(),
                row.getSnapshotAt(),
                row.getPeriodStartDate(),
                row.getAccountSuffix(),
                row.getLabel(),
                row.getAccountKind(),
                scaleMoney(row.getTotalAccountValue()),
                scaleMoney(row.getCashBalance()),
                scaleMoney(row.getEquityMarketValue()),
                scaleMoney(row.getPeriodAdded()),
                scaleMoney(row.getPeriodRemoved()),
                scaleMoney(row.getPeriodValueChange()),
                holdings,
                flows);
    }

    private static List<RobinhoodRhCashFlowEventDto> flowsInPeriod(
            List<RobinhoodRhCashFlowEventDto> all, LocalDate periodStartExclusive, LocalDate periodEndInclusive) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhCashFlowEventDto> out = new ArrayList<>();
        for (RobinhoodRhCashFlowEventDto event : all) {
            if ("STARTING_BALANCE".equals(event.flowCategory())) {
                continue;
            }
            if (event.activityDate() == null) {
                continue;
            }
            if (periodStartExclusive != null
                    && (event.activityDate().isBefore(periodStartExclusive)
                            || event.activityDate().equals(periodStartExclusive))) {
                continue;
            }
            if (event.activityDate().isAfter(periodEndInclusive)) {
                continue;
            }
            out.add(event);
        }
        return out;
    }

    private static BigDecimal sumFlowAmounts(List<RobinhoodRhCashFlowEventDto> flows, boolean added) {
        BigDecimal total = BigDecimal.ZERO;
        for (RobinhoodRhCashFlowEventDto flow : flows) {
            if ("IN".equals(flow.direction()) && added) {
                total = total.add(nullToZero(flow.amount()));
            } else if ("OUT".equals(flow.direction()) && !added) {
                total = total.add(nullToZero(flow.amount()));
            }
        }
        return total;
    }

    private static BigDecimal latestCombinedTotal(List<RobinhoodRhDailySnapshot> rows) {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate latest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latest == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = rows.stream()
                .filter(r -> latest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .reduce(BigDecimal.ZERO, RobinhoodRhDailyTrackerService::nullToZero);
        return scaleMoney(sum);
    }

    private static BigDecimal combinedChange(List<RobinhoodRhDailySnapshot> rows) {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate earliest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate latest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (earliest == null || latest == null || earliest.equals(latest)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal start = rows.stream()
                .filter(r -> earliest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .reduce(BigDecimal.ZERO, RobinhoodRhDailyTrackerService::nullToZero);
        BigDecimal end = rows.stream()
                .filter(r -> latest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .reduce(BigDecimal.ZERO, RobinhoodRhDailyTrackerService::nullToZero);
        return scaleMoney(end.subtract(start));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            log.warn("Could not serialize snapshot JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Could not parse snapshot JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
