package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ManagementDayOneLogWriteRequest {
    @NotNull private LocalDate loggedOn;
    @NotBlank private String entryText;
}
