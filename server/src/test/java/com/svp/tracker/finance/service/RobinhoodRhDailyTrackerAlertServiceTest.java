package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.RhDailyTrackerAccountAlert;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RobinhoodRhDailyTrackerAlertServiceTest {

    @Test
    void firesWhenDollarThresholdMet() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setValueDollarsEnabled(true);
        config.setMinValueChangeDollars(new BigDecimal("500"));

        assertTrue(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("600"), Optional.empty(), false));
        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("400"), Optional.empty(), false));
    }

    @Test
    void firesWhenPercentThresholdMet() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setValuePercentEnabled(true);
        config.setMinValueChangePercent(new BigDecimal("2"));

        assertTrue(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, BigDecimal.ZERO, Optional.of(new BigDecimal("2.5")), false));
        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, BigDecimal.ZERO, Optional.of(new BigDecimal("1.5")), false));
    }

    @Test
    void skipsPercentWhenPriorTotalZero() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setValuePercentEnabled(true);
        config.setMinValueChangePercent(new BigDecimal("1"));

        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("1000"), Optional.empty(), false));
    }

    @Test
    void firesOnPositionChangeOnly() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setPositionChangeEnabled(true);

        assertTrue(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, BigDecimal.ZERO, Optional.empty(), true));
        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, BigDecimal.ZERO, Optional.empty(), false));
    }

    @Test
    void combinedOrLogic() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setValueDollarsEnabled(true);
        config.setMinValueChangeDollars(new BigDecimal("1000"));
        config.setPositionChangeEnabled(true);

        assertTrue(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("50"), Optional.empty(), true));
        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("50"), Optional.empty(), false));
    }

    @Test
    void disabledConfigNeverFires() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setEnabled(false);
        config.setValueDollarsEnabled(true);
        config.setMinValueChangeDollars(new BigDecimal("1"));

        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("9999"), Optional.of(new BigDecimal("99")), true));
    }

    @Test
    void enabledWithoutTriggersNeverFires() {
        RhDailyTrackerAccountAlert config = enabledConfig();

        assertFalse(RobinhoodRhDailyTrackerAlertService.shouldFire(
                config, new BigDecimal("9999"), Optional.of(new BigDecimal("99")), true));
    }

    @Test
    void cooldownSuppressesRepeat() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setCooldownMinutes(60);
        config.setLastTriggeredAt(Instant.parse("2026-07-04T19:00:00Z"));

        assertTrue(RobinhoodRhDailyTrackerAlertService.withinCooldown(
                config, Instant.parse("2026-07-04T19:30:00Z")));
        assertFalse(RobinhoodRhDailyTrackerAlertService.withinCooldown(
                config, Instant.parse("2026-07-04T20:05:00Z")));
    }

    @Test
    void firedReasonsListsMatchingTriggers() {
        RhDailyTrackerAccountAlert config = enabledConfig();
        config.setValueDollarsEnabled(true);
        config.setMinValueChangeDollars(new BigDecimal("100"));
        config.setPositionChangeEnabled(true);

        var reasons = RobinhoodRhDailyTrackerAlertService.firedReasons(
                config, new BigDecimal("150"), Optional.empty(), true);
        assertTrue(reasons.contains("VALUE_DOLLARS"));
        assertTrue(reasons.contains("POSITIONS"));
    }

    private static RhDailyTrackerAccountAlert enabledConfig() {
        RhDailyTrackerAccountAlert c = new RhDailyTrackerAccountAlert();
        c.setEnabled(true);
        c.setCooldownMinutes(60);
        return c;
    }
}
