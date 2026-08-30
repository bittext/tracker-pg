package com.svp.tracker.finance.dto;

public record CompanyFinancialsSearchHistoryItemDto(
        long id, String symbol, String companyName, String searchedAt) {}
