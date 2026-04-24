package com.svp.tracker.finance.dto;

public record IndexSnapshotDto(String symbol, String shortName, Double price, Double changePercent) {}
