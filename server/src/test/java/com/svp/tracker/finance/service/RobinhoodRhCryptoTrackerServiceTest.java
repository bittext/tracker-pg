package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerReportDto;
import com.svp.tracker.finance.repository.RobinhoodRhCryptoSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class RobinhoodRhCryptoTrackerServiceTest {

    private static final long OWNER = 42L;

    private CurrentUserService currentUser;
    private AppUserRepository appUserRepository;
    private RobinhoodAgenticProperties agenticProps;
    private RobinhoodRhCryptoTrackerProperties cryptoTrackerProps;
    private RobinhoodCryptoTradingService cryptoTradingService;
    private RobinhoodRhCryptoSnapshotRepository snapshotRepository;
    private RobinhoodRhCryptoTrackerService service;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserService.class);
        appUserRepository = mock(AppUserRepository.class);
        agenticProps = mock(RobinhoodAgenticProperties.class);
        cryptoTrackerProps = new RobinhoodRhCryptoTrackerProperties("0 0 * * * *", "true", "America/Chicago", 21);
        cryptoTradingService = mock(RobinhoodCryptoTradingService.class);
        snapshotRepository = mock(RobinhoodRhCryptoSnapshotRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RobinhoodRhCryptoTrackerService> selfProvider = mock(ObjectProvider.class);
        service = new RobinhoodRhCryptoTrackerService(
                currentUser,
                appUserRepository,
                agenticProps,
                cryptoTrackerProps,
                cryptoTradingService,
                snapshotRepository,
                selfProvider);
        when(selfProvider.getObject()).thenReturn(service);
        when(currentUser.requireUserId()).thenReturn(OWNER);
        when(appUserRepository.findById(OWNER)).thenReturn(Optional.empty());
    }

    @Test
    void buildReport_groupsSameDayAndChangesFromPriorClose() {
        when(agenticProps.serviceConfigured()).thenReturn(true);
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(true);
        when(snapshotRepository.countByOwnerUserId(OWNER)).thenReturn(3L);

        Instant d1 = Instant.parse("2026-07-03T21:00:00-05:00");
        Instant d2am = Instant.parse("2026-07-04T10:00:00-05:00");
        Instant d2pm = Instant.parse("2026-07-04T21:00:00-05:00");
        List<RobinhoodRhCryptoSnapshot> rows = List.of(
                snapshot(d2pm, "3370", RobinhoodRhDailyCaptureKind.SCHEDULED, "10000.00", holding("BTC", "0.1", "50000", "5000")),
                snapshot(d2am, "3370", RobinhoodRhDailyCaptureKind.INTRADAY, "9000.00", holding("BTC", "0.1", "50000", "5000")),
                snapshot(d1, "3370", RobinhoodRhDailyCaptureKind.SCHEDULED, "8000.00", holding("BTC", "0.1", "50000", "5000")));

        when(snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescSnapshotAtDesc(
                        eq(OWNER), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rows);

        RobinhoodRhCryptoTrackerReportDto report = service.buildReport(2026, List.of(7));

        assertEquals(RobinhoodRhCryptoTrackerService.STATUS_READY, report.status());
        assertEquals(2, report.days().size());
        assertEquals(LocalDate.of(2026, 7, 4), report.days().get(0).snapshotDate());
        assertEquals(new BigDecimal("10000.00"), report.days().get(0).totalValue());
        assertEquals(new BigDecimal("2000.00"), report.days().get(0).changeFromPrevious());
        assertEquals(1, report.days().get(0).intradayCaptures().size());
        assertEquals(new BigDecimal("8000.00"), report.days().get(1).totalValue());
        assertEquals(new BigDecimal("0.00"), report.days().get(1).changeFromPrevious());
        assertTrue(report.autoCaptureScheduleLabel().contains("9:00 PM"));
    }

    @Test
    void buildReport_notConnectedWhenMissingCredentials() {
        when(agenticProps.serviceConfigured()).thenReturn(true);
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(false);
        when(snapshotRepository.countByOwnerUserId(OWNER)).thenReturn(0L);
        when(snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescSnapshotAtDesc(
                        eq(OWNER), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        RobinhoodRhCryptoTrackerReportDto report = service.buildReport(2026, List.of());

        assertEquals(RobinhoodRhCryptoTrackerService.STATUS_NOT_CONNECTED, report.status());
        assertFalse(report.cryptoConnected());
    }

    @Test
    void captureForOwner_persistsMappedAccountsAndLeavesCloseAlone() {
        Instant at = Instant.parse("2026-07-05T15:00:00Z");
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(true);
        RobinhoodRhCryptoHoldingDto btc = holding("BTC", "0.5", "60000", "30000");
        when(cryptoTradingService.syncForOwner(OWNER))
                .thenReturn(new RobinhoodCryptoTradingSyncResultDto(
                        true,
                        "Synced 1 crypto holding(s) across 1 account(s).",
                        "311209094705",
                        new BigDecimal("30000.00"),
                        List.of(btc),
                        List.of(),
                        List.of(new RobinhoodCryptoTradingPortfolioDto(
                                "311209094705", new BigDecimal("30000.00"), List.of(btc)))));

        RobinhoodRhCryptoCaptureResultDto result =
                service.captureForOwner(OWNER, at, RobinhoodRhDailyCaptureKind.MANUAL, true);

        assertTrue(result.ok());
        assertEquals(1, result.holdingsCaptured());
        assertTrue(result.message().contains("manual"));
        assertTrue(result.message().contains("9 PM CT"));

        ArgumentCaptor<RobinhoodRhCryptoSnapshot> saved = ArgumentCaptor.forClass(RobinhoodRhCryptoSnapshot.class);
        verify(snapshotRepository, times(1)).save(saved.capture());
        RobinhoodRhCryptoSnapshot row = saved.getValue();
        assertEquals(OWNER, row.getOwnerUserId());
        assertEquals("3370", row.getAccountSuffix());
        assertEquals(RobinhoodRhDailyCaptureKind.MANUAL, row.getCaptureKind());
        assertEquals(new BigDecimal("30000.00"), row.getTotalValue());
        assertTrue(row.getHoldingsJson().contains("\"BTC\""));
    }

    private static RobinhoodRhCryptoSnapshot snapshot(
            Instant at, String suffix, String kind, String total, RobinhoodRhCryptoHoldingDto... holdings) {
        RobinhoodRhCryptoSnapshot row = new RobinhoodRhCryptoSnapshot();
        row.setOwnerUserId(OWNER);
        row.setSnapshotAt(at);
        row.setSnapshotDate(at.atZone(java.time.ZoneId.of("America/Chicago")).toLocalDate());
        row.setCaptureKind(kind);
        row.setAccountSuffix(suffix);
        row.setLabel("Individual a/c (...3370)");
        row.setTotalValue(new BigDecimal(total));
        row.setHoldingsJson(toJson(holdings));
        row.setCreatedAt(at);
        return row;
    }

    private static String toJson(RobinhoodRhCryptoHoldingDto... holdings) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < holdings.length; i++) {
            RobinhoodRhCryptoHoldingDto h = holdings[i];
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"symbol\":\"")
                    .append(h.symbol())
                    .append("\",\"quantity\":")
                    .append(h.quantity())
                    .append(",\"currentUnitPrice\":")
                    .append(h.currentUnitPrice())
                    .append(",\"marketValue\":")
                    .append(h.marketValue())
                    .append(",\"averageBuyPrice\":0,\"costBasis\":0,\"unrealizedPnL\":0,\"unrealizedPnLPercent\":0}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static RobinhoodRhCryptoHoldingDto holding(
            String symbol, String qty, String unitPrice, String marketValue) {
        return new RobinhoodRhCryptoHoldingDto(
                symbol,
                new BigDecimal(qty),
                BigDecimal.ZERO,
                new BigDecimal(unitPrice),
                new BigDecimal(marketValue),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
