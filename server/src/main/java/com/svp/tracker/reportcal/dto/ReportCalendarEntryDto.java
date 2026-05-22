package com.svp.tracker.reportcal.dto;

import com.svp.tracker.reportcal.domain.ReportCalendarType;
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
    ReportCalendarType calendarType;
    String title;
    String body;
    List<ReportCalendarAttachmentDto> attachments;
    Instant createdAt;
    Instant updatedAt;
}
