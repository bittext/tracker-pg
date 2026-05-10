package com.svp.tracker.management.dto;

import com.svp.tracker.management.domain.TravelPlaceStatus;
import java.time.LocalDate;

/** Lightweight row for map markers and list overlays. */
public record TravelPlaceMapDto(
        long id,
        long tripId,
        String tripTitle,
        String tripColorHex,
        String name,
        double latitude,
        double longitude,
        TravelPlaceStatus placeStatus,
        LocalDate visitDate) {}
