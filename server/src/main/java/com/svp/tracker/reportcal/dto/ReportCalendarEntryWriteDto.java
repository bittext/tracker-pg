package com.svp.tracker.reportcal.dto;

import com.svp.tracker.reportcal.domain.ReportCalendarType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ReportCalendarEntryWriteDto {
    @NotNull
    private LocalDate entryDate;

    @NotNull
    private ReportCalendarType calendarType;

    private String title;

    private String body;
}
