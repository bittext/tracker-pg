package com.svp.tracker.member.dto;

import java.util.List;

/** Result of a US ZIP lookup (Zippopotam). Empty places when ZIP is unknown. */
public record UsPostalValidationResponseDto(
        String postalCode, List<UsPostalPlaceDto> places, String source, String message) {

    public record UsPostalPlaceDto(String placeName, String stateAbbreviation, String stateName) {}
}
