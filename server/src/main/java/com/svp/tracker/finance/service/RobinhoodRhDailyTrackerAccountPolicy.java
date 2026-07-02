package com.svp.tracker.finance.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Absolute Daily Tracker account scope per Robinhood Agentic MCP profile.
 *
 * <ul>
 *   <li>{@value #SPULICKAL_USERNAME} — pulickal-agentic: ••••3370, ••••3550, ••••4123, ••••8696 (never ••••0440 / ••••2835)
 *   <li>{@value #NISHA_USERNAME} — nisha-agentic: every account suffix from her own sync (••••4190, ••••7581, …)
 *   <li>Other users — disabled until {@code tracker.finance.rh-daily-tracker.additional-owner-suffixes} is set
 * </ul>
 *
 * A suffix must appear in the owner's synced {@code ownedSuffixes} so cross-user data never leaks.
 */
final class RobinhoodRhDailyTrackerAccountPolicy {

    static final String SPULICKAL_USERNAME = "spulickal";
    static final String NISHA_USERNAME = "nisha";

    /** pulickal-agentic Daily Tracker allowlist (must also be present in that user's Agentic sync). */
    static final Set<String> SPULICKAL_PROFILE_SUFFIXES = Set.of("3370", "3550", "4123", "8696");

    /** Always hidden for spulickal Daily Tracker (still synced elsewhere). */
    static final Set<String> SPULICKAL_EXCLUDED_SUFFIXES = Set.of("0440", "2835");

    private RobinhoodRhDailyTrackerAccountPolicy() {}

    static boolean isUserEnabled(String username, Map<String, Set<String>> additionalOwnerSuffixes) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String normalized = normalizeUsername(username);
        if (SPULICKAL_USERNAME.equals(normalized) || NISHA_USERNAME.equals(normalized)) {
            return true;
        }
        Set<String> configured = additionalOwnerSuffixes.get(normalized);
        return configured != null && !configured.isEmpty();
    }

    static boolean matches(
            String username,
            Set<String> ownedSuffixes,
            String suffix,
            Map<String, Set<String>> additionalOwnerSuffixes) {
        if (suffix == null || suffix.isBlank() || RobinhoodAccountTrackerConfigService.isUnsetSuffix(suffix)) {
            return false;
        }
        if (!isUserEnabled(username, additionalOwnerSuffixes)) {
            return false;
        }
        String accountSuffix = suffix.trim();
        if (ownedSuffixes == null || !ownedSuffixes.contains(accountSuffix)) {
            return false;
        }
        String user = normalizeUsername(username);
        if (SPULICKAL_USERNAME.equals(user)) {
            if (SPULICKAL_EXCLUDED_SUFFIXES.contains(accountSuffix)) {
                return false;
            }
            return SPULICKAL_PROFILE_SUFFIXES.contains(accountSuffix);
        }
        if (NISHA_USERNAME.equals(user)) {
            return true;
        }
        Set<String> configured = additionalOwnerSuffixes.get(user);
        return configured != null && configured.contains(accountSuffix);
    }

    static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
