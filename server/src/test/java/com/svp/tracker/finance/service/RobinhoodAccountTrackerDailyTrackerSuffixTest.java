package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Guards Daily Tracker account scope: nisha gets all owned sync accounts; spulickal keeps exclusions. */
class RobinhoodAccountTrackerDailyTrackerSuffixTest {

    @Test
    void nonOwnerIncludesEveryOwnedSuffixFromAgenticSync() {
        Set<String> owned = Set.of("4190", "7581");
        assertTrue(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                false, owned, Set.of("7581"), "4190"));
        assertTrue(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                false, owned, Set.of("7581"), "7581"));
    }

    @Test
    void nonOwnerRejectsSuffixesNotInTheirSync() {
        Set<String> owned = Set.of("4190", "7581");
        assertFalse(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                false, owned, owned, "3370"));
        assertFalse(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                false, owned, owned, "3550"));
    }

    @Test
    void spulickalUsesTrackedSuffixesWithExclusions() {
        Set<String> owned = Set.of("3370", "3550", "4123", "0440", "2835");
        Set<String> tracked = Set.of("3370", "3550", "4123");
        assertTrue(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                true, owned, tracked, "3550"));
        assertFalse(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                true, owned, tracked, "0440"));
        assertFalse(RobinhoodAccountTrackerConfigService.matchesDailyTrackerSuffixPolicy(
                true, owned, tracked, "4190"));
    }
}
