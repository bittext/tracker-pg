package com.svp.tracker.finance.service;

import java.util.Map;
import java.util.Optional;

/**
 * Maps Robinhood crypto (rhc) account numbers onto Daily Tracker brokerage suffixes so Crypto
 * Tracker can keep the same books: Individual ••••3370, Agentic ••••3550, Ammu ••••8696.
 * Short Term ••••0440 and Roth ••••2835 stay excluded.
 */
final class RobinhoodRhCryptoAccountPolicy {

    private static final Map<String, String> RHC_TO_SUFFIX = Map.of(
            "311209094705", "3370",
            "311263615767", "3550",
            "311263756272", "8696",
            "311263254385", "0440");

    private RobinhoodRhCryptoAccountPolicy() {}

    static String suffixForCryptoAccount(String rhcAccountNumber) {
        if (rhcAccountNumber == null || rhcAccountNumber.isBlank()) {
            return "";
        }
        String number = rhcAccountNumber.trim();
        String mapped = RHC_TO_SUFFIX.get(number);
        if (mapped != null) {
            return mapped;
        }
        return number.length() <= 4 ? number : number.substring(number.length() - 4);
    }

    static boolean includeForUser(String username, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        if (username == null || username.isBlank()) {
            return !RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_EXCLUDED_SUFFIXES.contains(suffix);
        }
        return RobinhoodRhDailyTrackerAccountPolicy.matches(username, null, suffix, Map.of());
    }

    static Optional<String> labelForSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix));
    }
}
