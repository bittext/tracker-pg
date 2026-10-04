package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/** Opening balances captured from Plaid on the 1st of each month. */
public record BankingPlaidOpeningBalancesDto(int year, List<Month> months) {

    public record Month(String key, List<Group> groups) {}

    public record Group(String institution, BigDecimal total, List<Account> accounts) {}

    public record Account(String label, BigDecimal amount) {}
}
