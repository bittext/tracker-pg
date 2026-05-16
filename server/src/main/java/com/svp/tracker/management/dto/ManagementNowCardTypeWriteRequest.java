package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManagementNowCardTypeWriteRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[a-z][a-z0-9-]{0,62}$", message = "slug must start with a letter and use lowercase letters, digits, or hyphen")
    private String slug;

    @NotBlank
    @Size(max = 120)
    private String label;

    @NotBlank
    @Size(max = 32)
    private String badge;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorHex must be a 6-digit hex color like #6366f1")
    private String colorHex;

    private Integer sortIndex;
}
