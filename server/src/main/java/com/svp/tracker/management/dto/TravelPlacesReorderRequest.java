package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TravelPlacesReorderRequest(@NotEmpty List<Long> orderedPlaceIds) {}
