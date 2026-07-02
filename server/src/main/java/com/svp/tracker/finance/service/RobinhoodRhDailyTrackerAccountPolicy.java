package com.svp.tracker.finance.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Absolute Daily Tracker account scope per Robinhood Agentic MCP profile.
 *
 * <ul>
 *   <li>{@value #SPULICKAL_USERNAME} — pulickal-agentic: ••••3370, ••••3550, ••••4123, ••••8696 (never ••••0440 / ••••2835)
 *   <li>{@value #NISHA_USERNAME} — nisha-agentic: ••••4190 (default), ••••7581 (Agentic) only
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

    /** nisha-agentic Daily Tracker allowlist (must also be present in that user's Agentic sync). */
    static final Set<String> NISHA_PROFILE_SUFFIXES = Set.of("4190", "7581");

    /** Never shown on non-spulickal Daily Tracker even if stale sync rows reference them. */
    static final Set<String> PULICKAL_AGENTIC_SUFFIXES =
            Set.of("3370", "3550", "4123", "8696", "0440", "2835");

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

    static Set<String> profileSuffixesForUser(String username, Map<String, Set<String>> additionalOwnerSuffixes) {
        if (!isUserEnabled(username, additionalOwnerSuffixes)) {
            return Set.of();
        }
        String user = normalizeUsername(username);
        if (SPULICKAL_USERNAME.equals(user)) {
            return SPULICKAL_PROFILE_SUFFIXES;
        }
        if (NISHA_USERNAME.equals(user)) {
            return NISHA_PROFILE_SUFFIXES;
        }
        Set<String> configured = additionalOwnerSuffixes.get(user);
        return configured == null ? Set.of() : Set.copyOf(configured);
    }

    static Set<String> effectiveDailyTrackerSuffixes(
            String username, Set<String> syncedOwnedSuffixes, Map<String, Set<String>> additionalOwnerSuffixes) {
        if (username == null || username.isBlank() || !isUserEnabled(username, additionalOwnerSuffixes)) {
            return Set.of();
        }
        String user = normalizeUsername(username);
        if (SPULICKAL_USERNAME.equals(user)) {
            return SPULICKAL_PROFILE_SUFFIXES.stream()
                    .filter(s -> syncedOwnedSuffixes == null
                            || syncedOwnedSuffixes.isEmpty()
                            || syncedOwnedSuffixes.contains(s))
                    .filter(s -> !SPULICKAL_EXCLUDED_SUFFIXES.contains(s))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (NISHA_USERNAME.equals(user)) {
            return NISHA_PROFILE_SUFFIXES.stream()
                    .filter(s -> syncedOwnedSuffixes == null
                            || syncedOwnedSuffixes.isEmpty()
                            || syncedOwnedSuffixes.contains(s))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (syncedOwnedSuffixes == null || syncedOwnedSuffixes.isEmpty()) {
            return Set.of();
        }
        Set<String> configured = additionalOwnerSuffixes.get(user);
        if (configured == null || configured.isEmpty()) {
            return Set.of();
        }
        return syncedOwnedSuffixes.stream()
                .filter(configured::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static boolean matches(
            String username,
            Set<String> ownedSuffixes,
            String suffix,
            Map<String, Set<String>> additionalOwnerSuffixes) {
        if (suffix == null || suffix.isBlank() || RobinhoodAccountTrackerConfigService.isUnsetSuffix(suffix)) {
            return false;
        }
        String accountSuffix = suffix.trim();
        String user = normalizeUsername(username);
        if (!SPULICKAL_USERNAME.equals(user) && PULICKAL_AGENTIC_SUFFIXES.contains(accountSuffix)) {
            return false;
        }
        if (SPULICKAL_USERNAME.equals(user)) {
            if (SPULICKAL_EXCLUDED_SUFFIXES.contains(accountSuffix)) {
                return false;
            }
            return SPULICKAL_PROFILE_SUFFIXES.contains(accountSuffix);
        }
        if (NISHA_USERNAME.equals(user)) {
            return NISHA_PROFILE_SUFFIXES.contains(accountSuffix);
        }
        Set<String> configured = additionalOwnerSuffixes.get(user);
        if (configured == null || !configured.contains(accountSuffix)) {
            return false;
        }
        return ownedSuffixes != null && ownedSuffixes.contains(accountSuffix);
    }

    static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
