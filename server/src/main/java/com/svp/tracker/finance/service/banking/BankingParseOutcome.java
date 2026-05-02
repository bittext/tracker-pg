package com.svp.tracker.finance.service.banking;

import java.util.List;

public record BankingParseOutcome(List<BankingParsedRow> rows, String note) {}
