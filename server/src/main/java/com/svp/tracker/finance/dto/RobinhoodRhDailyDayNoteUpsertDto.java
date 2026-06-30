package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record RobinhoodRhDailyDayNoteUpsertDto(LocalDate snapshotDate, String noteText) {}
