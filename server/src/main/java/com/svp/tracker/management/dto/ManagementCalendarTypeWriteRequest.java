package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManagementCalendarTypeWriteRequest {

    @NotBlank
    @Size(max = 32)
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{0,30}$",
            message = "code must start with a letter and use uppercase letters, digits, or underscore")
    private String code;

    @NotBlank
    @Size(max = 120)
    private String label;

    private Integer sortIndex;
}
