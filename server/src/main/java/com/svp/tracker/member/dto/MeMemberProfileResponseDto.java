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
        Long memberPublicId,
        /** When true, email is stored on {@code auth_users} and cannot be edited from this profile. */
        boolean contactEmailLockedFromAuth,
        /** When true, phone is stored on {@code auth_users.phone_e164} and cannot be edited from this profile. */
        boolean contactPhoneLockedFromAuth,
        /**
         * When {@code contactPhoneLockedFromAuth}, the E.164 value shown as a single read-only line (all formats). Null
         * when the phone is not locked.
         */
        String accountPhoneE164) {}
