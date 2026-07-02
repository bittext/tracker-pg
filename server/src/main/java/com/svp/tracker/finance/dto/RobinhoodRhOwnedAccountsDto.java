package com.svp.tracker.finance.dto;

import java.util.Set;

/** Per-owner Robinhood account suffixes derived from synced portfolios/positions (not global defaults). */
public record RobinhoodRhOwnedAccountsDto(
        String individualSuffix,
        String agenticSuffix,
        String managedSuffix,
        Set<String> ownedSuffixes,
        Set<String> trackedSuffixes) {}
