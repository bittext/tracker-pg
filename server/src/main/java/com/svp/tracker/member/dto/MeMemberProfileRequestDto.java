package com.svp.tracker.member.dto;

import com.svp.tracker.member.domain.MemberGender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MeMemberProfileRequestDto(
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        @Size(max = 80) String nickname,
        @NotNull LocalDate dateOfBirth,
        MemberGender gender,
        @NotBlank @Email String email,
        @NotBlank String phoneCountryCode,
        @NotBlank String phoneNationalNumber,
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        @NotBlank String stateRegion,
        @NotBlank String postalCode,
        boolean addressUseValidatedSuggestion,
        boolean marketingEmailOptIn,
        boolean marketingSmsOptIn) {}
