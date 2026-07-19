package com.svp.tracker.finance.dto;

/**
 * Parameters for the long-call options backtest.
 *
 * <p>Premiums are Black–Scholes proxies using realized volatility from the underlying — not live option
 * chain quotes. Educational simulation only.
 */
public record OptionsBacktestRequestDto(
        String symbol,
        /** Calendar lookback in trading days of history to fetch (default 252 ≈ 1y). */
        Integer lookbackDays,
        /** Starting cash for the simulation. */
        Double startingCapital,
        /** Call strike as percent above spot (e.g. 5 = 5% OTM; 0 = ATM). */
        Double callOtmPercent,
        /** Days to expiration for each long call. */
        Integer daysToExpiration,
        /** Annual risk-free rate used in Black–Scholes (e.g. 0.04). */
        Double riskFreeRate) {}
