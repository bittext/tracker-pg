package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RhDailyTrackerAccountAlertSaveRequestDto(List<RhDailyTrackerAccountAlertItemDto> accounts) {}
