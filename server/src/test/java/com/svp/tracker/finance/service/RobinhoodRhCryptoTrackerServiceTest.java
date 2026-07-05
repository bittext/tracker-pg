package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodRhCryptoSnapshot;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.dto.RobinhoodCryptoTradingSyncResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoCaptureResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerReportDto;
import com.svp.tracker.finance.repository.RobinhoodRhCryptoSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class RobinhoodRhCryptoTrackerServiceTest {

    private static final long OWNER = 42L;

    private CurrentUserService currentUser;
    private RobinhoodAgenticProperties agenticProps;
    private RobinhoodRhCryptoTrackerProperties cryptoTrackerProps;
    private RobinhoodCryptoTradingService cryptoTradingService;
    private RobinhoodRhCryptoSnapshotRepository snapshotRepository;
    private RobinhoodRhCryptoTrackerService service;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserService.class);
        agenticProps = mock(RobinhoodAgenticProperties.class);
        cryptoTrackerProps = new RobinhoodRhCryptoTrackerProperties("0 0 */4 * * *", "true");
        cryptoTradingService = mock(RobinhoodCryptoTradingService.class);
        snapshotRepository = mock(RobinhoodRhCryptoSnapshotRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RobinhoodRhCryptoTrackerService> selfProvider = mock(ObjectProvider.class);
        service = new RobinhoodRhCryptoTrackerService(
                currentUser,
                agenticProps,
                cryptoTrackerProps,
                cryptoTradingService,
                snapshotRepository,
                selfProvider);
        when(selfProvider.getObject()).thenReturn(service);
        when(currentUser.requireUserId()).thenReturn(OWNER);
    }

    @Test
    void buildReport_computesChangeFromPrevious() {
        when(agenticProps.serviceConfigured()).thenReturn(true);
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(true);
        when(snapshotRepository.countByOwnerUserId(OWNER)).thenReturn(2L);

        Instant t1 = Instant.parse("2026-07-04T12:00:00Z");
        Instant t2 = Instant.parse("2026-07-04T18:00:00Z");
        List<RobinhoodRhCryptoSnapshot> rows = List.of(
                snapshot(t2, "10000.00", holding("BTC", "0.1", "50000", "5000")),
                snapshot(t1, "8000.00", holding("BTC", "0.1", "50000", "5000")));

        when(snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescSnapshotAtDesc(
                        eq(OWNER), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rows);

        RobinhoodRhCryptoTrackerReportDto report = service.buildReport(2026, List.of(7));

        assertEquals(RobinhoodRhCryptoTrackerService.STATUS_READY, report.status());
        assertTrue(report.cryptoConnected());
        assertEquals(2, report.days().size());
        assertEquals(new BigDecimal("10000.00"), report.days().get(0).totalValue());
        assertEquals(new BigDecimal("2000.00"), report.days().get(0).changeFromPrevious());
        assertEquals(new BigDecimal("8000.00"), report.days().get(1).totalValue());
        assertEquals(new BigDecimal("0.00"), report.days().get(1).changeFromPrevious());
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
    void captureForOwner_persistsSnapshotFromSync() {
        Instant at = Instant.parse("2026-07-05T15:00:00Z");
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(true);
        RobinhoodRhCryptoHoldingDto btc = holding("BTC", "0.5", "60000", "30000");
        when(cryptoTradingService.syncForOwner(OWNER))
                .thenReturn(new RobinhoodCryptoTradingSyncResultDto(
                        true,
                        "Synced 1 crypto holding(s).",
                        "acct-1",
                        new BigDecimal("30000.00"),
                        List.of(btc),
                        List.of()));

        RobinhoodRhCryptoCaptureResultDto result =
                service.captureForOwner(OWNER, at, RobinhoodRhDailyCaptureKind.MANUAL, true);

        assertTrue(result.ok());
        assertEquals(1, result.holdingsCaptured());
        assertTrue(result.message().contains("manual"));

        ArgumentCaptor<RobinhoodRhCryptoSnapshot> saved = ArgumentCaptor.forClass(RobinhoodRhCryptoSnapshot.class);
        verify(snapshotRepository).save(saved.capture());
        RobinhoodRhCryptoSnapshot row = saved.getValue();
        assertEquals(OWNER, row.getOwnerUserId());
        assertEquals(at, row.getSnapshotAt());
        assertEquals(new BigDecimal("30000.00"), row.getTotalValue());
        assertTrue(row.getHoldingsJson().contains("\"BTC\""));
    }

    private static RobinhoodRhCryptoSnapshot snapshot(
            Instant at, String total, RobinhoodRhCryptoHoldingDto... holdings) {
        RobinhoodRhCryptoSnapshot row = new RobinhoodRhCryptoSnapshot();
        row.setOwnerUserId(OWNER);
        row.setSnapshotAt(at);
        row.setSnapshotDate(at.atZone(java.time.ZoneId.of("America/Chicago")).toLocalDate());
        row.setCaptureKind(RobinhoodRhDailyCaptureKind.SCHEDULED);
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
