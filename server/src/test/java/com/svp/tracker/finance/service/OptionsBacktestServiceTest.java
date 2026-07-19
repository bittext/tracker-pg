package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.OptionsBacktestResultDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptionsBacktestServiceTest {

    @Test
    void blackScholesPutCallParityHoldsApproximately() {
        double spot = 100;
        double strike = 100;
        double t = 30 / 365.0;
        double r = 0.04;
        double sigma = 0.25;
        double call = OptionsBacktestService.blackScholesCall(spot, strike, t, r, sigma);
        double put = OptionsBacktestService.blackScholesPut(spot, strike, t, r, sigma);
        double parity = call - put - (spot - strike * Math.exp(-r * t));
        assertEquals(0.0, parity, 1e-6);
        assertTrue(call > 0 && put > 0);
    }

    @Test
    void longCallsProduceTradesOnSyntheticHistory() {
        FinanceProperties props = org.mockito.Mockito.mock(FinanceProperties.class);
        org.mockito.Mockito.when(props.newsTimeoutMs()).thenReturn(15_000);
        OptionsBacktestService service = new OptionsBacktestService(props);

        List<OptionsBacktestService.Bar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 2);
        double px = 100.0;
        for (int i = 0; i < 180; i++) {
            // Mild uptrend with noise so some calls finish ITM.
            px = px * (1.0 + ((i % 7) - 3) * 0.004);
            bars.add(new OptionsBacktestService.Bar(start.plusDays(i), Math.max(px, 40)));
        }

        OptionsBacktestResultDto result = service.simulateLongCalls("TEST", bars, 100_000, 0.05, 21, 0.04);
        assertEquals(OptionsBacktestService.STRATEGY_ID, result.strategyId());
        assertEquals("LONG_CALL", result.strategyId());
        assertFalse(result.trades().isEmpty());
        assertFalse(result.equityCurve().isEmpty());
        assertTrue(result.tradeCount() > 0);
        assertTrue(result.endingEquity().doubleValue() > 0);
        assertTrue(result.trades().stream().allMatch(t -> "BUY_CALL".equals(t.action())));
    }
}
