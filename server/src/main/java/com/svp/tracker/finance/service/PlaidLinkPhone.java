package com.svp.tracker.finance.service;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.member.domain.MemberProfile;
import jakarta.annotation.Nullable;
import org.springframework.util.StringUtils;

/**
 * Derives an optional E.164 phone for {@link com.plaid.client.model.LinkTokenCreateRequestUser}. Plaid rejects invalid
 * values; we only send a number when it survives normalization + ITU-style checks.
 */
public final class PlaidLinkPhone {

    private PlaidLinkPhone() {}

    /** Prefer MFA/account {@code auth_users.phone_e164}; otherwise member profile country + national digits. */
    @Nullable
    public static String resolveOrNull(@Nullable AppUser authUser, @Nullable MemberProfile profile) {
        if (authUser != null) {
            String fromAuth = normalizeE164OrNull(authUser.getPhoneE164());
            if (fromAuth != null) {
                return fromAuth;
            }
        }
        if (profile == null) {
            return null;
        }
        String cc = profile.getPhoneCountryCode();
        String nat = profile.getPhoneNationalNumber();
        if (!StringUtils.hasText(cc) || !StringUtils.hasText(nat)) {
            return null;
        }
        String normalizedCc = normalizeCountryCallingCode(cc);
        String nationalDigits = digitsOnly(nat);
        if (nationalDigits.isEmpty()) {
            return null;
        }
        return normalizeE164OrNull(normalizedCc + nationalDigits);
    }

    @Nullable
    static String normalizeE164OrNull(@Nullable String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (!t.startsWith("+")) {
            return null;
        }
        String digits = digitsOnly(t.substring(1));
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        if (digits.charAt(0) == '0') {
            return null;
        }
        return "+" + digits;
    }

    private static String normalizeCountryCallingCode(String code) {
        String c = code.trim();
        if (!c.startsWith("+")) {
            c = "+" + c.replace("+", "").trim();
        }
        return c;
    }

    private static String digitsOnly(String s) {
        return s.replaceAll("\\D", "");
    }
}
