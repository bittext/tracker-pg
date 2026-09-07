package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagementDueTransferClassifierTest {

    @Test
    void descriptionFlagsOwnAccountMoves() {
        assertTrue(ManagementDueTransferClassifier.isInternalTransferDescription("INTERNAL TRANSFER"));
        assertTrue(ManagementDueTransferClassifier.isInternalTransferDescription("Online Transfer to Savings"));
        assertTrue(ManagementDueTransferClassifier.isInternalTransferDescription("XFER to checking"));
        assertFalse(ManagementDueTransferClassifier.isInternalTransferDescription("COMED ELECTRIC BILL"));
    }

    @Test
    void pairingMarksOppositeLegsAcrossInstitutions() {
        List<ManagementDueTransferClassifier.TxnView> rows = List.of(
                new ManagementDueTransferClassifier.TxnView(
                        1L, 10L, LocalDate.of(2026, 9, 1), new BigDecimal("-250.00"), "Move"),
                new ManagementDueTransferClassifier.TxnView(
                        2L, 20L, LocalDate.of(2026, 9, 1), new BigDecimal("250.00"), "Move in"));
        Set<Long> ids = ManagementDueTransferClassifier.internalTransferIds(rows);
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    @Test
    void pairingIgnoresVendorPayments() {
        List<ManagementDueTransferClassifier.TxnView> rows = List.of(
                new ManagementDueTransferClassifier.TxnView(
                        3L, 10L, LocalDate.of(2026, 9, 2), new BigDecimal("-88.12"), "COMED"),
                new ManagementDueTransferClassifier.TxnView(
                        4L, 10L, LocalDate.of(2026, 9, 3), new BigDecimal("1200.00"), "PAYROLL"));
        Set<Long> ids = ManagementDueTransferClassifier.internalTransferIds(rows);
        assertFalse(ids.contains(3L));
        assertFalse(ids.contains(4L));
    }
}
