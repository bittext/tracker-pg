package com.svp.tracker.admin.dto;

import java.util.List;

public record ServerLogsDto(
        List<String> lines, int requestedLimit, int returned, int totalBuffered) {}
