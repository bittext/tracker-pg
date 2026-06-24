package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record RobinhoodAccountTrackerDto(
        Instant trackingStartedAt,
        String individualAccountSuffix,
        String agenticAccountSuffix,
        RobinhoodIndividualNbisTrackerDto individualNbis,
        RobinhoodAgenticSpxComparisonDto agenticVsSpx,
        List<RobinhoodAccountLedgerEventDto> ledgerEvents,
        List<String> notes) {}
