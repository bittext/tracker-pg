package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RobinhoodRhCryptoSymbolMapTest {

    @Test
    void toTradingPair_mapsAssetCodes() {
        assertEquals(Optional.of("BTC-USD"), RobinhoodRhCryptoSymbolMap.toTradingPair("BTC"));
        assertEquals(Optional.of("ETH-USD"), RobinhoodRhCryptoSymbolMap.toTradingPair("eth"));
        assertEquals(Optional.of("SOL-USD"), RobinhoodRhCryptoSymbolMap.toTradingPair("SOL-USD"));
    }

    @Test
    void toTradingPair_rejectsUnknownSymbols() {
        assertTrue(RobinhoodRhCryptoSymbolMap.toTradingPair("AAPL").isEmpty());
        assertTrue(RobinhoodRhCryptoSymbolMap.toTradingPair("").isEmpty());
    }

    @Test
    void toAssetCode_normalizesPair() {
        assertEquals("BTC", RobinhoodRhCryptoSymbolMap.toAssetCode("BTC-USD"));
        assertEquals("ETH", RobinhoodRhCryptoSymbolMap.toAssetCode("ETH"));
    }

    @Test
    void supportedAssets_includesMajors() {
        assertTrue(RobinhoodRhCryptoSymbolMap.supportedAssets().contains("BTC"));
        assertTrue(RobinhoodRhCryptoSymbolMap.supportedAssets().contains("DOGE"));
    }
}
