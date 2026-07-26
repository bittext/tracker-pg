package com.svp.tracker.finance.dto.finviz;

import java.util.List;
import java.util.Map;

public record FinvizEliteTableDto(
        String sourceLabel,
        List<String> columns,
        List<Map<String, String>> rows,
        String fetchedAt,
        boolean fromCache,
        String note) {}
