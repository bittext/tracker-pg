package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FinanceTaxDeskServiceSideTest {

    @Test
    void buyIncludesOptionOpenAndClose() {
        assertTrue(FinanceTaxDeskService.isBuySide("buy"));
        assertTrue(FinanceTaxDeskService.isBuySide("BUY_TO_OPEN"));
        assertTrue(FinanceTaxDeskService.isBuySide("buy to close"));
        assertFalse(FinanceTaxDeskService.isBuySide("sell"));
        assertFalse(FinanceTaxDeskService.isBuySide(null));
        assertFalse(FinanceTaxDeskService.isBuySide(""));
    }

    @Test
    void sellIncludesOptionOpenAndClose() {
        assertTrue(FinanceTaxDeskService.isSellSide("sell"));
        assertTrue(FinanceTaxDeskService.isSellSide("sell_to_close"));
        assertTrue(FinanceTaxDeskService.isSellSide("SELL TO OPEN"));
        assertFalse(FinanceTaxDeskService.isSellSide("buy"));
        assertFalse(FinanceTaxDeskService.isSellSide(null));
    }
}
