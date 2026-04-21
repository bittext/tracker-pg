package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class ManagementDayOneEntryWriteRequest {
    @NotNull private LocalDate loggedOn;
    @NotBlank private String entryText;
    private String locationText;
    private String weatherText;
    private List<Long> tagIds;
}
