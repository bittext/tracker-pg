package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelTripStatus;
import java.time.Instant;
import java.time.LocalDate;

public record TravelTripSummaryDto(
        long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TravelTripStatus status,
        String colorHex,
        long placeCount,
        Instant createdAt,
        Instant updatedAt) {}
