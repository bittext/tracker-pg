package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingSettings;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoAutoTradeEvaluateDto;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import com.svp.tracker.finance.predicts.service.PredictsService;
import com.svp.tracker.finance.repository.RobinhoodCryptoAutoTradeRunRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobinhoodRhCryptoAutoTradeServiceTest {

    private static final long OWNER = 7L;

    private RobinhoodAgenticProperties agenticProps;
    private RobinhoodRhCryptoAutoTradeProperties autoTradeProps;
    private RobinhoodCryptoTradingService cryptoTradingService;
    private RobinhoodCryptoTradingSettingsRepository settingsRepository;
    private RobinhoodCryptoOrderRepository orderRepository;
    private RobinhoodCryptoAutoTradeRunRepository runRepository;
    private RobinhoodRhCryptoOrderService orderService;
    private PredictsService predictsService;
    private PredictsTickerRepository tickerRepository;
    private RobinhoodRhCryptoAutoTradeService service;

    @BeforeEach
    void setUp() {
        agenticProps = mock(RobinhoodAgenticProperties.class);
        autoTradeProps = new RobinhoodRhCryptoAutoTradeProperties("true", "0 */5 * * * *");
        cryptoTradingService = mock(RobinhoodCryptoTradingService.class);
        settingsRepository = mock(RobinhoodCryptoTradingSettingsRepository.class);
        orderRepository = mock(RobinhoodCryptoOrderRepository.class);
        runRepository = mock(RobinhoodCryptoAutoTradeRunRepository.class);
        orderService = mock(RobinhoodRhCryptoOrderService.class);
        predictsService = mock(PredictsService.class);
        tickerRepository = mock(PredictsTickerRepository.class);
        service = new RobinhoodRhCryptoAutoTradeService(
                agenticProps,
                autoTradeProps,
                mock(com.svp.tracker.auth.security.CurrentUserService.class),
                cryptoTradingService,
                settingsRepository,
                orderRepository,
                runRepository,
                orderService,
                predictsService,
                tickerRepository);

        when(agenticProps.serviceConfigured()).thenReturn(true);
        when(cryptoTradingService.isConnected(OWNER)).thenReturn(true);
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void evaluateForUser_skipsWhenKillSwitchActive() {
        RobinhoodCryptoTradingSettings settings = enabledSettings();
        settings.setAutoTradeKillSwitch(true);
        when(settingsRepository.findByOwnerUserId(OWNER)).thenReturn(Optional.of(settings));

        RobinhoodRhCryptoAutoTradeEvaluateDto result = service.evaluateForUser(OWNER, false);

        assertFalse(result.ran());
        assertEquals("Kill switch active — auto-trade paused", result.message());
        verify(orderService, never()).placeAutoMarketOrder(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateForUser_skipsWhenDailyTradeLimitReached() {
        RobinhoodCryptoTradingSettings settings = enabledSettings();
        when(settingsRepository.findByOwnerUserId(OWNER)).thenReturn(Optional.of(settings));
        when(orderRepository.countActiveOrdersSince(eq(OWNER), eq("auto"), any(Instant.class)))
                .thenReturn(3L);

        RobinhoodRhCryptoAutoTradeEvaluateDto result = service.evaluateForUser(OWNER, false);

        assertTrue(result.ran());
        assertEquals("Daily trade limit reached", result.message());
        verify(tickerRepository, never()).findByOwnerUserIdOrderByAutoSeededAscSymbolAsc(anyLong());
    }

    @Test
    void isSuccessfulOrderStatus_recognizesPlacedStates() {
        assertTrue(RobinhoodRhCryptoAutoTradeService.isSuccessfulOrderStatus("placed"));
        assertTrue(RobinhoodRhCryptoAutoTradeService.isSuccessfulOrderStatus("filled"));
        assertFalse(RobinhoodRhCryptoAutoTradeService.isSuccessfulOrderStatus("failed"));
    }

    @Test
    void parseAllowedSymbols_readsJsonArray() {
        List<String> symbols = RobinhoodRhCryptoOrderService.parseAllowedSymbols("[\"BTC\",\"ETH\"]");
        assertEquals(List.of("BTC", "ETH"), symbols);
    }

    private static RobinhoodCryptoTradingSettings enabledSettings() {
        RobinhoodCryptoTradingSettings s = new RobinhoodCryptoTradingSettings();
        s.setOwnerUserId(OWNER);
        s.setAutoTradeEnabled(true);
        s.setAutoTradeKillSwitch(false);
        s.setAutoTradeMaxTradesPerDay(3);
        s.setAutoTradeOrderQuoteAmount(new BigDecimal("25.00"));
        s.setAllowedSymbolsJson("[\"BTC\",\"ETH\"]");
        return s;
    }
}
