package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManagementDueBankAccountSupportTest {

    @Test
    void acceptsDepositBankTypes() {
        assertTrue(ManagementDueBankAccountSupport.isBankingAccountType("Banking"));
        assertTrue(ManagementDueBankAccountSupport.isBankingAccountType("checking"));
        assertTrue(ManagementDueBankAccountSupport.isBankingAccountType("High-yield savings"));
        assertTrue(ManagementDueBankAccountSupport.isBankingAccountType("Money market"));
    }

    @Test
    void rejectsCardsLoansAndInvestments() {
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType("Credit Card"));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType("Credit Cards"));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType("Investment"));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType("Loans"));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType("Retirement"));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType(null));
        assertFalse(ManagementDueBankAccountSupport.isBankingAccountType(""));
    }
}
