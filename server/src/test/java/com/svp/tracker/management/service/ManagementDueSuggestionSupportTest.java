package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.management.domain.ManagementDueSide;
import com.svp.tracker.management.dto.ManagementDueSuggestionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagementDueSuggestionSupportTest {

    @Test
    void monthlyBillsForTheViewedMonthAndLargeOneOffDebits() {
        YearMonth october = YearMonth.of(2026, 10);
        List<ManagementDueSuggestionSupport.Txn> rows = List.of(
                debit(1, LocalDate.of(2026, 8, 15), "80", "COMED ELECTRIC"),
                debit(2, LocalDate.of(2026, 9, 15), "82", "COMED ELECTRIC"),
                debit(3, LocalDate.of(2026, 10, 15), "81", "COMED ELECTRIC"),
                debit(4, LocalDate.of(2026, 3, 22), "2400", "IRS USATAXPYMT"),
                debit(5, LocalDate.of(2025, 10, 8), "40", "NETFLIX.COM"),
                debit(6, LocalDate.of(2026, 4, 8), "40", "NETFLIX.COM"),
                debit(7, LocalDate.of(2026, 10, 3), "12.50", "COFFEE SHOP"));

        List<ManagementDueSuggestionDto> out =
                ManagementDueSuggestionSupport.build(rows, Set.of(), List.of(), october);

        assertEquals("COMED ELECTRIC", find(out, "COMED").counterparty());
        assertEquals(ManagementDueSuggestionSupport.KIND_MONTHLY, find(out, "COMED").kind());
        assertEquals(15, find(out, "COMED").typicalDay());
        assertEquals(ManagementDueSuggestionSupport.KIND_BIG_DEBIT, find(out, "IRS").kind());
        assertEquals(new BigDecimal("2400.00"), find(out, "IRS").estimatedAmount());
        assertTrue(out.stream().noneMatch(row -> row.counterparty().contains("COFFEE")));
    }

    @Test
    void skipsAlreadyTrackedAndInternalTransfers() {
        YearMonth october = YearMonth.of(2026, 10);
        List<ManagementDueSuggestionSupport.Txn> rows = List.of(
                debit(1, LocalDate.of(2026, 8, 1), "2000", "ACME RENT"),
                debit(2, LocalDate.of(2026, 9, 1), "2000", "ACME RENT"),
                debit(3, LocalDate.of(2026, 9, 4), "5000", "ONLINE TRANSFER"));

        List<ManagementDueSuggestionDto> out = ManagementDueSuggestionSupport.build(
                rows,
                Set.of(3L),
                List.of(new ManagementDueSuggestionSupport.Tracked(ManagementDueSide.PAYABLE, "Acme Rent")),
                october);

        assertTrue(out.isEmpty());
    }

    @Test
    void frequentMonthlyStillSuggestedWhenThisCalendarMonthIsMissing() {
        YearMonth october = YearMonth.of(2026, 10);
        List<ManagementDueSuggestionSupport.Txn> rows = List.of(
                debit(1, LocalDate.of(2026, 5, 3), "15", "SPOTIFY"),
                debit(2, LocalDate.of(2026, 6, 3), "15", "SPOTIFY"),
                debit(3, LocalDate.of(2026, 7, 3), "15", "SPOTIFY"));

        List<ManagementDueSuggestionDto> out =
                ManagementDueSuggestionSupport.build(rows, Set.of(), List.of(), october);

        assertEquals(ManagementDueSuggestionSupport.KIND_MONTHLY, find(out, "SPOTIFY").kind());
    }

    private static ManagementDueSuggestionSupport.Txn debit(long id, LocalDate date, String amount, String desc) {
        return new ManagementDueSuggestionSupport.Txn(id, date, new BigDecimal(amount).negate(), desc);
    }

    private static ManagementDueSuggestionDto find(List<ManagementDueSuggestionDto> rows, String needle) {
        return rows.stream()
                .filter(row -> row.counterparty().toUpperCase().contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + needle + " in " + rows));
    }
}
