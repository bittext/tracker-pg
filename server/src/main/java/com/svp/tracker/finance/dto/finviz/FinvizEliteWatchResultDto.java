package com.svp.tracker.finance.dto.finviz;

import java.util.List;

public record FinvizEliteWatchResultDto(int addedOrUpdated, List<String> symbols) {}
