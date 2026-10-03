package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.management.domain.ManagementSubscriptionBillingCycle;
import com.svp.tracker.management.domain.ManagementSubscriptionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ManagementSubscriptionSupportTest {

    @Test
    void rollsAnnualRenewalForwardWhenAutoRenewIsOn() {
        LocalDate enrolled = LocalDate.of(2026, 7, 26);
        LocalDate today = LocalDate.of(2026, 10, 3);
        LocalDate next = ManagementSubscriptionSupport.nextRenewal(
                enrolled,
                enrolled.plusYears(1),
                ManagementSubscriptionBillingCycle.ANNUAL,
                ManagementSubscriptionStatus.ACTIVE,
                true,
                today);
        assertEquals(LocalDate.of(2027, 7, 26), next);
        assertEquals(296, ManagementSubscriptionSupport.daysUntil(next, today));
    }

    @Test
    void refundWindowClosesAfterThirtyDays() {
        LocalDate enrolled = LocalDate.of(2026, 7, 26);
        assertTrue(ManagementSubscriptionSupport.refundWindowOpen(
                enrolled, ManagementSubscriptionStatus.ACTIVE, LocalDate.of(2026, 8, 25)));
        assertFalse(ManagementSubscriptionSupport.refundWindowOpen(
                enrolled, ManagementSubscriptionStatus.ACTIVE, LocalDate.of(2026, 8, 26)));
        assertFalse(ManagementSubscriptionSupport.refundWindowOpen(
                enrolled, ManagementSubscriptionStatus.CANCELLED, LocalDate.of(2026, 8, 1)));
    }
}
