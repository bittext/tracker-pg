package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobinhoodRhAccountsTrackServiceFlowTotalsTest {

    @Test
    void summarizeManagedCashFlowSeparatesStartingDepositsAndInternal() {
        List<RobinhoodRhCashFlowEventDto> events =
                List.of(
                        RobinhoodRhCashFlowAllocator.startingBalanceEvent(
                                LocalDate.of(2026, 4, 5), new BigDecimal("100.00")),
                        new RobinhoodRhCashFlowEventDto(
                                LocalDate.of(2026, 4, 5),
                                "IN",
                                new BigDecimal("750.00"),
                                "CDEP",
                                "Managed account deposit (YTD)",
                                "Config",
                                "EXTERNAL_IN",
                                false,
                                null),
                        new RobinhoodRhCashFlowEventDto(
                                LocalDate.of(2026, 6, 24),
                                "IN",
                                new BigDecimal("400.00"),
                                "ITRF",
                                "Transfer from Brokerage to Brokerage (mirrored)",
                                "Derived",
                                "INTERNAL_IN",
                                true,
                                "••••3370"));

        RobinhoodRhAccountsTrackService.FlowTotals totals =
                RobinhoodRhAccountsTrackService.summarizeFlows(events, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("1150.00"), totals.deposits());
        assertEquals(new BigDecimal("400.00"), totals.internalIn());
        assertEquals(new BigDecimal("1250.00"), totals.net());
    }
}
