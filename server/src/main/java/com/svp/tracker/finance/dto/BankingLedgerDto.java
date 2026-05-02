package com.svp.tracker.finance.dto;

import java.util.List;

public record BankingLedgerDto(
        boolean importDirectoryConfigured,
        String importDirectory,
        String rangeLabel,
        List<BankingInstitutionDto> institutions,
        List<BankingTransactionDto> transactions,
        List<BankingImportFileDto> importFiles) {}
