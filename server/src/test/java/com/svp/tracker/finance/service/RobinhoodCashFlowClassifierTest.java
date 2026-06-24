package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RobinhoodCashFlowClassifierTest {

    @Test
    void itrfIsInternalTransfer() {
        assertTrue(RobinhoodCashFlowClassifier.isInternalTransfer("ITRF", "Internal transfer"));
        assertEquals(
                "OUT",
                RobinhoodCashFlowClassifier.cashFlowDirection("ITRF", "Internal transfer", new BigDecimal("-1000")));
    }

    @Test
    void transferCodesAreCashFlow() {
        assertTrue(RobinhoodCashFlowClassifier.isCashFlowRow("Transfer", "Transfer to brokerage", null));
        assertTrue(RobinhoodCashFlowClassifier.isCashFlowRow("Transfer In", "Transfer from bank", null));
        assertTrue(RobinhoodCashFlowClassifier.isCashFlowRow("ACH", "ACH Deposit", null));
        assertTrue(RobinhoodCashFlowClassifier.isCashFlowRow("ITRF", "Internal transfer", null));
        assertTrue(RobinhoodCashFlowClassifier.isCashFlowRow("RTP", "Instant bank transfer", null));
    }

    @Test
    void tradesAreNotCashFlow() {
        assertFalse(RobinhoodCashFlowClassifier.isCashFlowRow("BTO", "NBIS", "NBIS"));
        assertFalse(RobinhoodCashFlowClassifier.isCashFlowRow("STC", "NBIS", "NBIS"));
    }

    @Test
    void directionUsesCodeAndAmount() {
        assertEquals(
                "IN",
                RobinhoodCashFlowClassifier.cashFlowDirection("Transfer In", null, new BigDecimal("500")));
        assertEquals(
                "OUT",
                RobinhoodCashFlowClassifier.cashFlowDirection("Transfer", null, new BigDecimal("-500")));
        assertEquals(
                "OUT",
                RobinhoodCashFlowClassifier.cashFlowDirection("Transfer Out", null, new BigDecimal("500")));
        assertEquals(
                "IN",
                RobinhoodCashFlowClassifier.cashFlowDirection("ACH", "ACH Deposit", new BigDecimal("1000")));
    }
}
