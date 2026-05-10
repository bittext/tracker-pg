package com.svp.tracker.management.dto;

/**
 * Result of forward-geocoding a free-text address or place query (e.g. via OpenStreetMap Nominatim).
 */
public record TravelGeocodeResultDto(
        double latitude,
        double longitude,
        String displayName,
        String country,
        String region,
        String locality) {}
