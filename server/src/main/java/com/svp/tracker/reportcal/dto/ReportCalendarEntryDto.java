package com.svp.tracker.reportcal.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportCalendarEntryDto {
    long id;
    LocalDate entryDate;
    String calendarType;
    String title;
    String body;
    String details;
    List<ReportCalendarAttachmentDto> attachments;
    Instant createdAt;
    Instant updatedAt;
}
