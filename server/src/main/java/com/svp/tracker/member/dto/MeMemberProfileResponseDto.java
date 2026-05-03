package com.svp.tracker.member.dto;

import com.svp.tracker.member.domain.MemberGender;
import java.time.LocalDate;

public record MeMemberProfileResponseDto(
        String firstName,
        String middleName,
        String lastName,
        String nickname,
        LocalDate dateOfBirth,
        MemberGender gender,
        String email,
        String phoneCountryCode,
        String phoneNationalNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String stateRegion,
        String postalCode,
        String validatedPostalCode,
        String validatedCity,
        String validatedStateRegion,
        boolean addressUseValidatedSuggestion,
        boolean marketingEmailOptIn,
        boolean marketingSmsOptIn,
        Long memberPublicId) {}
