package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record FinanceTaxDeskPageDto(
        int taxYear,
        LocalDate asOf,
        boolean live,
        FinanceTaxDeskWorkbookDto workbook,
        List<FinanceTaxDeskSnapshotSummaryDto> history) {}
