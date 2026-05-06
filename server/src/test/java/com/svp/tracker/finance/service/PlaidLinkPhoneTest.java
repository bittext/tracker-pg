package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.member.domain.MemberProfile;
import org.junit.jupiter.api.Test;

class PlaidLinkPhoneTest {

    @Test
    void resolve_prefers_auth_phone_e164() {
        AppUser u = new AppUser();
        u.setPhoneE164("+1 (415) 555-2671");
        MemberProfile p = new MemberProfile();
        p.setPhoneCountryCode("+44");
        p.setPhoneNationalNumber("7700900123");
        assertEquals("+14155552671", PlaidLinkPhone.resolveOrNull(u, p));
    }

    @Test
    void resolve_falls_back_to_profile_when_auth_missing() {
        AppUser u = new AppUser();
        MemberProfile p = new MemberProfile();
        p.setPhoneCountryCode("1");
        p.setPhoneNationalNumber("4155552671");
        assertEquals("+14155552671", PlaidLinkPhone.resolveOrNull(u, p));
    }

    @Test
    void resolve_null_when_auth_invalid_and_profile_incomplete() {
        AppUser u = new AppUser();
        u.setPhoneE164("4155552671");
        MemberProfile p = new MemberProfile();
        assertNull(PlaidLinkPhone.resolveOrNull(u, p));
    }

    @Test
    void normalize_rejects_short_numbers() {
        assertNull(PlaidLinkPhone.normalizeE164OrNull("+1234567"));
    }
}
