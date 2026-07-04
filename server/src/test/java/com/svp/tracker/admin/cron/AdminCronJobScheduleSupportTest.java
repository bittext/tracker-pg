package com.svp.tracker.admin.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminCronJobScheduleSupportTest {

    @Test
    void normalizeCronExpression_acceptsUnixFiveField() {
        assertThat(AdminCronJobScheduleSupport.normalizeCronExpression("40,50 8 * * 1-5"))
                .isEqualTo("0 40,50 8 * * 1-5");
    }

    @Test
    void normalizeCronExpression_preservesSixField() {
        assertThat(AdminCronJobScheduleSupport.normalizeCronExpression("0 17 3 * * *"))
                .isEqualTo("0 17 3 * * *");
    }

    @Test
    void normalizeCronExpression_rejectsWrongFieldCount() {
        assertThatThrownBy(() -> AdminCronJobScheduleSupport.normalizeCronExpression("* * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 3");
    }
}
