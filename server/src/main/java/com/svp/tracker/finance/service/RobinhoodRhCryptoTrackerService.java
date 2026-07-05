package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodRhCryptoSnapshot;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoCaptureResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerReportDto;
import com.svp.tracker.finance.repository.RobinhoodRhCryptoSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Crypto holdings timeline for Reports → Crypto Tracker (separate from Daily Tracker). */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhCryptoTrackerService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    public static final String STATUS_READY = "READY";
    public static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";

    private final CurrentUserService currentUser;
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
        rows = rows.stream()
                .filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter))
                .sorted(Comparator.comparing(RobinhoodRhCryptoSnapshot::getSnapshotAt).reversed())
                .toList();

        List<RobinhoodRhCryptoTrackerDayDto> days = buildDaysWithChanges(rows);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodRhCryptoCaptureResultDto captureScheduledForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.SCHEDULED, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        List<RobinhoodRhCryptoHoldingDto> holdings = syncResult.holdings();
        BigDecimal totalValue = syncResult.totalValue() == null ? BigDecimal.ZERO : syncResult.totalValue();

        LocalDate snapshotDate = snapshotAt.atZone(CENTRAL).toLocalDate();
        RobinhoodRhCryptoSnapshot snapshot = new RobinhoodRhCryptoSnapshot();
        snapshot.setOwnerUserId(ownerUserId);
        snapshot.setSnapshotAt(snapshotAt);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setCaptureKind(captureKind);
        snapshot.setTotalValue(scaleMoney(totalValue));
        snapshot.setHoldingsJson(writeJson(holdings));
        snapshot.setCreatedAt(Instant.now());
        snapshotRepository.save(snapshot);

        int count = holdings.size();
        String message = RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind)
                ? "Saved manual crypto capture (" + count + " coin" + (count == 1 ? "" : "s") + ")."
                : "Saved crypto capture (" + count + " coin" + (count == 1 ? "" : "s") + ").";
        return new RobinhoodRhCryptoCaptureResultDto(true, snapshotAt, message, count);
    }

    private List<RobinhoodRhCryptoTrackerDayDto> buildDaysWithChanges(List<RobinhoodRhCryptoSnapshot> rowsNewestFirst) {
        if (rowsNewestFirst.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhCryptoSnapshot> chronological = new ArrayList<>(rowsNewestFirst);
        chronological.sort(Comparator.comparing(RobinhoodRhCryptoSnapshot::getSnapshotAt));

        Map<Instant, RobinhoodRhCryptoSnapshot> byInstant = new LinkedHashMap<>();
        for (RobinhoodRhCryptoSnapshot row : chronological) {
            byInstant.put(row.getSnapshotAt(), row);
        }

        BigDecimal priorTotal = null;
        Map<String, BigDecimal> priorQtyBySymbol = new HashMap<>();
        Map<Instant, RobinhoodRhCryptoTrackerDayDto> built = new LinkedHashMap<>();

        for (RobinhoodRhCryptoSnapshot row : byInstant.values()) {
            List<RobinhoodRhCryptoHoldingDto> holdings = readHoldings(row.getHoldingsJson());
            BigDecimal total = nullToZero(row.getTotalValue());
            BigDecimal changeFromPrevious = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (priorTotal != null) {
                changeFromPrevious = scaleMoney(total.subtract(priorTotal));
            }

            List<RobinhoodRhCryptoHoldingDto> holdingsWithDelta = new ArrayList<>();
            for (RobinhoodRhCryptoHoldingDto h : holdings) {
                String sym = h.symbol() == null ? "" : h.symbol().trim().toUpperCase();
                BigDecimal qty = nullToZero(h.quantity());
                BigDecimal priorQty = priorQtyBySymbol.getOrDefault(sym, BigDecimal.ZERO);
                holdingsWithDelta.add(h);
                priorQtyBySymbol.put(sym, qty);
            }

            built.put(
                    row.getSnapshotAt(),
                    new RobinhoodRhCryptoTrackerDayDto(
                            row.getSnapshotDate(),
                            row.getSnapshotAt(),
                            row.getCaptureKind(),
                            scaleMoney(total),
                            changeFromPrevious,
                            List.copyOf(holdingsWithDelta)));

            priorTotal = total;
        }

        List<RobinhoodRhCryptoTrackerDayDto> out = new ArrayList<>(built.values());
        out.sort(Comparator.comparing(RobinhoodRhCryptoTrackerDayDto::snapshotAt).reversed());
        return out;
    }

    private List<RobinhoodRhCryptoHoldingDto> readHoldings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RobinhoodRhCryptoHoldingDto> rows =
                    objectMapper.readValue(json, new TypeReference<>() {});
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
                "Uses Robinhood's official Crypto Trading API (trading.robinhood.com), separate from Agentic MCP "
                        + "and Daily Tracker brokerage snapshots.");
        if (!sidecarConfigured) {
            notes.add("Robinhood sidecar is not configured on this server.");
        } else if (!cryptoConnected) {
            notes.add(
                    "Add read-only Crypto Trading API credentials below (create keys in Robinhood crypto account settings on web).");
        } else if (snapshotCount == 0) {
            notes.add("Connected. Use Capture now or wait for the scheduled crypto capture job.");
        }
        if (props.snapshotSchedulerActive()) {
            notes.add("Auto-capture cron: " + props.snapshotCron());
        }
        notes.add("Cost basis and unrealized P&L are not available from holdings-only sync yet.");
        return List.copyOf(notes);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return nullToZero(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v;
    }
}
