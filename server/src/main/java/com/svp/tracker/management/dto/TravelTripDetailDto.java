package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelTripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TravelTripDetailDto(
        long id,
        long ownerUserId,
        String title,
        String summary,
        LocalDate startDate,
        LocalDate endDate,
        TravelTripStatus status,
        String colorHex,
        List<TravelPlaceDto> places,
        Instant createdAt,
        Instant updatedAt) {}
