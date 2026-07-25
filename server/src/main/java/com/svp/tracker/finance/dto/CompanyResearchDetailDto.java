package com.svp.tracker.finance.dto;

import java.util.List;

/** Full company research detail for the Watch drawer. */
public record CompanyResearchDetailDto(
        CompanyResearchCardDto card,
        CompanyQuoteSnapshotDto quote,
        List<CompanyEarningsHistoryRowDto> earningsHistory,
        StockNewsDto news,
        List<CompanyResearchNoteDto> notes) {}
