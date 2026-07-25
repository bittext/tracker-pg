package com.svp.tracker.finance.dto;

import java.util.List;

/** Scheduled-close holdings for one account on a journal day. */
public record TradingJournalAccountHoldingsDto(
        String accountSuffix, String label, List<RobinhoodRhHoldingDto> holdings) {}
