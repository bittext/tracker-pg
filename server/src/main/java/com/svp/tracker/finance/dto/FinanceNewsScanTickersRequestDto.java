package com.svp.tracker.finance.dto;

/** Replace the saved scan list. {@code tickers} is comma, space, or newline separated. */
public record FinanceNewsScanTickersRequestDto(String tickers) {}
