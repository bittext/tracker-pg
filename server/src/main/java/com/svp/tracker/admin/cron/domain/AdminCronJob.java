package com.svp.tracker.admin.cron.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_cron_job")
@Getter
@Setter
public class AdminCronJob {

    @Id
    @Column(name = "job_key", nullable = false, length = 64)
    private String jobKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "schedule_type", nullable = false, length = 16)
    private String scheduleType;

    @Column(name = "cron_expression", length = 128)
    private String cronExpression;

    @Column(name = "fixed_delay_ms")
    private Long fixedDelayMs;

    @Column(name = "initial_delay_ms", nullable = false)
    private long initialDelayMs;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = true;

    @Column(name = "runner_key", nullable = false, length = 64)
    private String runnerKey;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_status", length = 16)
    private String lastRunStatus;

    @Column(name = "last_run_message")
    private String lastRunMessage;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
