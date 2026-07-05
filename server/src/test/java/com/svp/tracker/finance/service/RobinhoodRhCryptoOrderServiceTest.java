package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobinhoodRhCryptoOrderServiceTest {

    private RobinhoodRhCryptoOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new RobinhoodRhCryptoOrderService(
                mock(RobinhoodAgenticProperties.class),
                new RobinhoodRhCryptoAutoTradeProperties("false", ""),
                mock(com.svp.tracker.auth.security.CurrentUserService.class),
                mock(com.svp.tracker.finance.repository.RobinhoodCryptoTradingConnectionRepository.class),
                mock(com.svp.tracker.finance.repository.RobinhoodCryptoTradingSettingsRepository.class),
                mock(com.svp.tracker.finance.repository.RobinhoodCryptoOrderRepository.class),
                mock(RobinhoodAgenticSidecarClient.class),
                mock(RobinhoodAgenticTokenCrypto.class),
                mock(RobinhoodCryptoTradingService.class));
    }

    @Test
    void isSymbolAllowed_respectsWhitelist() {
        RobinhoodCryptoTradingSettings settings = new RobinhoodCryptoTradingSettings();
        settings.setAllowedSymbolsJson("[\"BTC\",\"ETH\"]");

        assertTrue(orderService.isSymbolAllowed("BTC", settings));
        assertTrue(orderService.isSymbolAllowed("BTC-USD", settings));
        assertFalse(orderService.isSymbolAllowed("SOL", settings));
    }

    @Test
    void isSymbolAllowed_allowsAllWhenEmpty() {
        RobinhoodCryptoTradingSettings settings = new RobinhoodCryptoTradingSettings();
        settings.setAllowedSymbolsJson("[]");

        assertTrue(orderService.isSymbolAllowed("DOGE", settings));
    }
}
