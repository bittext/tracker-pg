package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncedOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailyDayNoteRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RobinhoodRhDailyTrackerTradesTest {

    private RobinhoodRhDailyTrackerService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RobinhoodRhDailyTrackerService> selfProvider = mock(ObjectProvider.class);
        service = new RobinhoodRhDailyTrackerService(
                mock(com.svp.tracker.auth.security.CurrentUserService.class),
                mock(com.svp.tracker.auth.repository.AppUserRepository.class),
                mock(RobinhoodRhAccountsTrackService.class),
                mock(RobinhoodAgenticService.class),
                mock(RobinhoodAgenticConnectionRepository.class),
                mock(RobinhoodAgenticSyncedOrderRepository.class),
                mock(RobinhoodRhDailySnapshotRepository.class),
                mock(RobinhoodRhDailyDayNoteRepository.class),
                mock(RobinhoodAccountTrackerConfigService.class),
                mock(com.svp.tracker.config.RobinhoodAgenticProperties.class),
                mock(com.svp.tracker.config.RobinhoodRhDailyTrackerProperties.class),
                selfProvider);
        when(selfProvider.getObject()).thenReturn(service);
    }

    @Test
    void buildDayTrades_fallsBackToIntradayWhenScheduledEmpty() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        RobinhoodRhDailySnapshot intraday = snapshot(
                RobinhoodRhDailyCaptureKind.INTRADAY,
                day,
                Instant.parse("2026-06-30T20:00:00Z"),
                "3370",
                "[{\"symbol\":\"AAPL\",\"side\":\"buy\",\"orderType\":\"market\",\"quantity\":1,\"averagePrice\":200,\"limitPrice\":null,\"state\":\"filled\",\"executedAt\":\"2026-06-30T19:30:00Z\"}]");

        List<RobinhoodRhDailyTradeDto> trades = service.buildDayTrades(
                day,
                List.of(),
                List.of(intraday),
                List.of(),
                LocalDate.of(2026, 6, 27),
                List.of(),
                Map.of("3370", new RobinhoodRhDailyTrackerAccountColumnDto("3370", "Default", "BROKERAGE")));

        assertEquals(1, trades.size());
        assertEquals("AAPL", trades.get(0).symbol());
        assertEquals("3370", trades.get(0).accountSuffix());
    }

    @Test
    void buildDayTrades_usesSyncedOrdersWhenSnapshotsHaveNoTrades() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        RobinhoodAgenticSyncedOrder order = new RobinhoodAgenticSyncedOrder();
        order.setAccountNumber("****3370");
        order.setSymbol("TSLA");
        order.setSide("buy");
        order.setOrderType("market");
        order.setQuantity(BigDecimal.ONE);
        order.setAveragePrice(new BigDecimal("250"));
        order.setState("filled");
        order.setUpdatedAtRh(Instant.parse("2026-06-30T15:00:00Z"));

        List<RobinhoodRhDailyTradeDto> trades = service.buildDayTrades(
                day,
                List.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 6, 27),
                List.of(order),
                Map.of("3370", new RobinhoodRhDailyTrackerAccountColumnDto("3370", "Default", "BROKERAGE")));

        assertEquals(1, trades.size());
        assertEquals("TSLA", trades.get(0).symbol());
    }

    @Test
    void buildDayTrades_prefersScheduledSnapshotTrades() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        RobinhoodRhDailySnapshot scheduled = snapshot(
                RobinhoodRhDailyCaptureKind.SCHEDULED,
                day,
                Instant.parse("2026-07-01T02:00:00Z"),
                "3370",
                "[{\"symbol\":\"NVDA\",\"side\":\"sell\",\"orderType\":\"limit\",\"quantity\":2,\"averagePrice\":120,\"limitPrice\":120,\"state\":\"filled\",\"executedAt\":\"2026-06-30T21:00:00Z\"}]");
        RobinhoodRhDailySnapshot intraday = snapshot(
                RobinhoodRhDailyCaptureKind.INTRADAY,
                day,
                Instant.parse("2026-06-30T14:00:00Z"),
                "3370",
                "[{\"symbol\":\"AAPL\",\"side\":\"buy\",\"orderType\":\"market\",\"quantity\":1,\"averagePrice\":200,\"limitPrice\":null,\"state\":\"filled\",\"executedAt\":\"2026-06-30T13:00:00Z\"}]");

        List<RobinhoodRhDailyTradeDto> trades = service.buildDayTrades(
                day,
                List.of(scheduled),
                List.of(intraday),
                List.of(),
                LocalDate.of(2026, 6, 27),
                List.of(),
                Map.of("3370", new RobinhoodRhDailyTrackerAccountColumnDto("3370", "Default", "BROKERAGE")));

        assertEquals(1, trades.size());
        assertEquals("NVDA", trades.get(0).symbol());
    }

    @Test
    void buildDayTrades_dedupesSameTrade() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        RobinhoodRhDailySnapshot a = snapshot(
                RobinhoodRhDailyCaptureKind.SCHEDULED,
                day,
                Instant.parse("2026-07-01T02:00:00Z"),
                "3370",
                "[{\"symbol\":\"NVDA\",\"side\":\"sell\",\"orderType\":\"limit\",\"quantity\":2,\"averagePrice\":120,\"limitPrice\":120,\"state\":\"filled\",\"executedAt\":\"2026-06-30T21:00:00Z\"}]");
        RobinhoodRhDailySnapshot b = snapshot(
                RobinhoodRhDailyCaptureKind.SCHEDULED,
                day,
                Instant.parse("2026-07-01T02:00:00Z"),
                "3550",
                "[{\"symbol\":\"NVDA\",\"side\":\"sell\",\"orderType\":\"limit\",\"quantity\":2,\"averagePrice\":120,\"limitPrice\":120,\"state\":\"filled\",\"executedAt\":\"2026-06-30T21:00:00Z\"}]");

        List<RobinhoodRhDailyTradeDto> trades =
                service.buildDayTrades(day, List.of(a, b), List.of(), List.of(), null, List.of(), Map.of());

        assertTrue(trades.size() >= 1);
    }

    private static RobinhoodRhDailySnapshot snapshot(
            String kind, LocalDate date, Instant at, String suffix, String tradesJson) {
        RobinhoodRhDailySnapshot row = new RobinhoodRhDailySnapshot();
        row.setCaptureKind(kind);
        row.setSnapshotDate(date);
        row.setSnapshotAt(at);
        row.setAccountSuffix(suffix);
        row.setLabel("Test " + suffix);
        row.setPeriodStartDate(date.minusDays(1));
        row.setTradesJson(tradesJson);
        return row;
    }
}
