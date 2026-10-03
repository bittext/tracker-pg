package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.management.domain.ManagementAutoPaymentFrequency;
import com.svp.tracker.management.domain.ManagementAutoPaymentStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ManagementAutoPaymentSupportTest {

    @Test
    void rollsMonthlyDebitForwardWhenActive() {
        LocalDate started = LocalDate.of(2026, 1, 15);
        LocalDate today = LocalDate.of(2026, 10, 3);
        LocalDate next = ManagementAutoPaymentSupport.nextDebit(
                started,
                LocalDate.of(2026, 1, 15),
                15,
                ManagementAutoPaymentFrequency.MONTHLY,
                ManagementAutoPaymentStatus.ACTIVE,
                today);
        assertEquals(LocalDate.of(2026, 10, 15), next);
        assertEquals(12, ManagementAutoPaymentSupport.daysUntil(next, today));
    }

    @Test
    void pausedKeepsTheStoredNextPayment() {
        LocalDate stored = LocalDate.of(2026, 9, 1);
        LocalDate next = ManagementAutoPaymentSupport.nextDebit(
                LocalDate.of(2026, 1, 1),
                stored,
                1,
                ManagementAutoPaymentFrequency.MONTHLY,
                ManagementAutoPaymentStatus.PAUSED,
                LocalDate.of(2026, 10, 3));
        assertEquals(stored, next);
    }

    @Test
    void dayOfMonthFillsMissingNextPayment() {
        LocalDate next = ManagementAutoPaymentSupport.nextDebit(
                LocalDate.of(2026, 7, 1),
                null,
                28,
                ManagementAutoPaymentFrequency.MONTHLY,
                ManagementAutoPaymentStatus.ACTIVE,
                LocalDate.of(2026, 10, 3));
        assertEquals(LocalDate.of(2026, 10, 28), next);
    }

    @Test
    void otherFrequencyDoesNotInventADate() {
        assertNull(ManagementAutoPaymentSupport.nextDebit(
                LocalDate.of(2026, 1, 1),
                null,
                null,
                ManagementAutoPaymentFrequency.OTHER,
                ManagementAutoPaymentStatus.ACTIVE,
                LocalDate.of(2026, 10, 3)));
    }
}
