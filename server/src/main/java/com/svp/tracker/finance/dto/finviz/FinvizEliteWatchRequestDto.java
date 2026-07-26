package com.svp.tracker.finance.dto.finviz;

import java.util.List;

public record FinvizEliteWatchRequestDto(List<String> symbols, String thesisTag) {}
