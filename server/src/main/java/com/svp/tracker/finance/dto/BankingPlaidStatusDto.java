package com.svp.tracker.finance.dto;

public record BankingPlaidStatusDto(boolean plaidConfigured, boolean linked, String itemIdSuffix) {}
