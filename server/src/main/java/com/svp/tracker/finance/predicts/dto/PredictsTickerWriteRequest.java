package com.svp.tracker.finance.predicts.dto;

import java.util.List;

/**
 * Add/update payload from clients. {@code sourcesEnabled} is the list of wire tokens
 * (e.g. ["stocktwits", "reddit"]); empty/null defaults to stocktwits.
 */
public record PredictsTickerWriteRequest(String symbol, List<String> sourcesEnabled, String note) {}
