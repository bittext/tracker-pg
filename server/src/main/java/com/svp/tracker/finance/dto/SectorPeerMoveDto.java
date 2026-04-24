package com.svp.tracker.finance.dto;

/** Another name in the Yahoo screener mix that shares a sector with the main swing; snapshot only. */
public record SectorPeerMoveDto(
        String symbol, String shortName, Double regularMarketChangePercent, Double regularMarketPrice, String sector) {}
