package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelPlaceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TravelPlaceWriteRequest(
        @NotBlank String name,
        double latitude,
        double longitude,
        String address,
        @NotNull TravelPlaceStatus placeStatus,
        LocalDate visitDate,
        String notes,
        int sortOrder) {}
