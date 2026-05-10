package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelPlaceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TravelPlaceDto(
        long id,
        long tripId,
        String tripTitle,
        String name,
        double latitude,
        double longitude,
        String address,
        TravelPlaceStatus placeStatus,
        LocalDate visitDate,
        String notes,
        int sortOrder,
        List<TravelPlacePhotoDto> photos,
        Instant createdAt,
        Instant updatedAt) {}
