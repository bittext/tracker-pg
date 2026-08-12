package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record RobinhoodSelectiveTradeCalendarDayDto(
        LocalDate date, int entryCount, int worked, int didnt, int mixed) {}
