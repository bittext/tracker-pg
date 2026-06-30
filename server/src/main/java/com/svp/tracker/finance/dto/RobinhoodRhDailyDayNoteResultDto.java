package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record RobinhoodRhDailyDayNoteResultDto(LocalDate snapshotDate, String noteText, String message) {}
