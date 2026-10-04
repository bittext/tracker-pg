package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BankingPlaidOpeningNameTest {

    @Test
    void instituteIsTheNameBeforeTheAccount() {
        assertEquals("Chase", BankingPlaidService.instituteName("Chase · TOTAL CHECKING (CHECKING) · …0404"));
        assertEquals("Bank of America", BankingPlaidService.instituteName("Bank of America · Regular Savings (SAVINGS) · …3885"));
        assertEquals("Robinhood", BankingPlaidService.instituteName("Robinhood · Robinhood Savings (SAVINGS) · …3848"));
    }

    @Test
    void instituteFallsBackToTheWholeName() {
        assertEquals("Chase", BankingPlaidService.instituteName("Chase"));
        assertEquals("Bank", BankingPlaidService.instituteName("  "));
    }
}
