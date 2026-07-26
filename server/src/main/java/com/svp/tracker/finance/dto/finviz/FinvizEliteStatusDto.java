package com.svp.tracker.finance.dto.finviz;

public record FinvizEliteStatusDto(
        boolean enabled, boolean configured, boolean universeEnabled, String note) {}
