package com.svp.tracker.finance.dto;

import java.util.List;

/** Screen: recent 52-week gainers ranked by rolling-high persistence. */
public record Surge52WeekHighsDto(
        String source,
        String fetchedAt,
        int returned,
        String note,
        List<Surge52WeekRowDto> rows) {}
