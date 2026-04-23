package com.svp.tracker.reportcal.dto;

import com.svp.tracker.reportcal.domain.ReportCalendarType;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportCalendarEntryDto {
    long id;
    LocalDate entryDate;
    ReportCalendarType calendarType;
    String title;
    String body;
    Instant createdAt;
    Instant updatedAt;
}
