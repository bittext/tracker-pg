package com.svp.tracker.finance.service;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Saves tax-desk working papers after the Daily Tracker 9 PM CT close. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceTaxDeskSnapshotScheduler {

    private final FinanceTaxDeskService taxDeskService;

    public void captureDailyWorkbooks() {
        LocalDate asOf = LocalDate.now(ZoneId.of("America/Chicago"));
        int year = asOf.getYear();
        for (Long owner : taxDeskService.snapshotOwnerIds()) {
            if (owner == null) {
                continue;
            }
            try {
                taxDeskService.loadForOwner(owner, year, asOf, true);
                log.info("Tax desk snapshot ok for user {} on {}", owner, asOf);
            } catch (Exception e) {
                log.warn("Tax desk snapshot failed for user {}: {}", owner, e.getMessage());
            }
        }
    }
}
