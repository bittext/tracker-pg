package com.svp.tracker.reportcal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ReportCalendarEntryWriteDto {
    @NotNull
    private LocalDate entryDate;

    @NotBlank
    private String calendarType;

    private String title;

    private String body;

    private String details;
}
