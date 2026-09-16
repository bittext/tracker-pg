package com.svp.tracker.finance.taxdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FederalTaxDeskCalculatorTest {

    @Test
    void pdfReferenceCaseMatchesWorkingPapers() {
        FederalTaxDeskCalculator.Result r = FederalTaxDeskCalculator.compute(new FederalTaxDeskCalculator.Input(
                FederalTaxDeskCalculator.MFJ,
                new BigDecimal("225814"),
                BigDecimal.ZERO,
                new BigDecimal("266459"),
                new BigDecimal("116898"),
                new BigDecimal("9654"),
                new BigDecimal("2700"),
                new BigDecimal("22601"),
                List.of(),
                new BigDecimal("191416"),
                new BigDecimal("29239"),
                new BigDecimal("5000"),
                LocalDate.of(2026, 9, 13),
                2026));
        assertEquals(0, new BigDecimal("365721.00").compareTo(r.agi()));
        assertEquals(0, new BigDecimal("333521.00").compareTo(r.taxableIncome()));
        assertEquals(0, new BigDecimal("65241.04").compareTo(r.incomeTax()));
        assertEquals(0, new BigDecimal("62541.04").compareTo(r.taxAfterCredits()));
        assertEquals(0, new BigDecimal("4397.40").compareTo(r.niit()));
        assertEquals(0, new BigDecimal("66938.44").compareTo(r.federalTax()));
        assertEquals(0, new BigDecimal("44337.44").compareTo(r.filingBalance()));
        assertEquals(4, r.quarters().size());
        assertEquals(LocalDate.of(2026, 9, 15), r.quarters().get(2).dueDate());
        assertEquals(LocalDate.of(2027, 1, 15), r.quarters().get(3).dueDate());
        assertTrue(r.requiredAnnualPayment().compareTo(new BigDecimal("32162.90")) == 0);
        assertFalse(r.safeHarborCovered());
        assertEquals("PENALTY_RISK", r.riskLevel());
    }

    @Test
    void parseEuropeanAndUsMoneyFromCalendarBodies() {
        assertEquals(0, new BigDecimal("10000.00").compareTo(FederalTaxDeskCalculator.parseMoney("$10.000.00 Quarterly payment")));
        assertEquals(0, new BigDecimal("1000.00").compareTo(FederalTaxDeskCalculator.parseMoney("IRS - 2026 Estimated Tax", "$1,000.00")));
        assertEquals(0, new BigDecimal("5000.00").compareTo(FederalTaxDeskCalculator.parseMoney("$5,000.00")));
    }

    @Test
    void indiaTaxIsNotIrs() {
        assertFalse(FederalTaxDeskCalculator.looksLikeIrs("India Taxes", ""));
        assertTrue(FederalTaxDeskCalculator.looksLikeIrs("IRS - Estimated Tax", "$10.000.00"));
        assertTrue(FederalTaxDeskCalculator.looksLikeIrs("IRS (1040-ES Estimated Tax)", "towards 2026 Estimated Tax"));
    }

    @Test
    void overpayPutsFilingOnRefundTrack() {
        FederalTaxDeskCalculator.Result r = FederalTaxDeskCalculator.compute(new FederalTaxDeskCalculator.Input(
                FederalTaxDeskCalculator.MFJ,
                new BigDecimal("225814"),
                BigDecimal.ZERO,
                new BigDecimal("266459"),
                new BigDecimal("126552"),
                BigDecimal.ZERO,
                new BigDecimal("2700"),
                new BigDecimal("22601"),
                List.of(
                        new FederalTaxDeskCalculator.Payment(LocalDate.of(2026, 8, 10), new BigDecimal("1000")),
                        new FederalTaxDeskCalculator.Payment(LocalDate.of(2026, 9, 13), new BigDecimal("10000")),
                        new FederalTaxDeskCalculator.Payment(LocalDate.of(2026, 9, 15), new BigDecimal("5000")),
                        new FederalTaxDeskCalculator.Payment(LocalDate.of(2027, 1, 15), new BigDecimal("40000"))),
                new BigDecimal("224000"),
                new BigDecimal("29239"),
                new BigDecimal("5000"),
                LocalDate.of(2026, 9, 16),
                2026));
        assertTrue(r.refundTargetCovered());
        assertTrue(r.safeHarborCovered());
        assertEquals("REFUND_TRACK", r.riskLevel());
    }
}
