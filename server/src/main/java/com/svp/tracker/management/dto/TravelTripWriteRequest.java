package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelTripStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TravelTripWriteRequest(
        @NotBlank String title,
        String summary,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull TravelTripStatus status,
        String colorHex) {}
