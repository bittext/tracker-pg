package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Absolute Daily Tracker scope: pulickal-agentic (spulickal), nisha-agentic (nisha), others disabled. */
class RobinhoodAccountTrackerDailyTrackerSuffixTest {

    private static final Map<String, Set<String>> NO_ADDITIONAL = Map.of();

    @Test
    void nishaIncludesEveryOwnedSuffixFromNishaAgentic() {
        Set<String> owned = Set.of("4190", "7581");
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "4190", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "7581", NO_ADDITIONAL));
    }

    @Test
    void nishaRejectsPulickalAgenticSuffixesEvenWhenSyncIsContaminated() {
        Set<String> owned = Set.of("4190", "7581", "3370", "3550", "4123");
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "3370", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "3550", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "4123", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "4190", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME, owned, "7581", NO_ADDITIONAL));
    }

    @Test
    void spulickalUsesPulickalAgenticAllowlistAndHardExclusions() {
        Set<String> owned = Set.of("3370", "3550", "4123", "0440", "2835", "8696");
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "3370", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "3550", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "4123", NO_ADDITIONAL));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "8696", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "0440", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "2835", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches(
                RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME, owned, "4190", NO_ADDITIONAL));
    }

    @Test
    void otherUsersDisabledUntilConfigured() {
        Set<String> owned = Set.of("1234", "5678");
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.isUserEnabled("someone", NO_ADDITIONAL));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches("someone", owned, "1234", NO_ADDITIONAL));

        Map<String, Set<String>> configured = Map.of("someone", Set.of("1234"));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.isUserEnabled("someone", configured));
        assertTrue(RobinhoodRhDailyTrackerAccountPolicy.matches("someone", owned, "1234", configured));
        assertFalse(RobinhoodRhDailyTrackerAccountPolicy.matches("someone", owned, "5678", configured));
    }
}
