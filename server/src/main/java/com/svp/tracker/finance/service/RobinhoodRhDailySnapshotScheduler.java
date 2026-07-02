package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Captures RH account snapshots daily at 9 PM Central (configurable). */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
        "'${tracker.finance.rh-daily-tracker.snapshot-scheduler-enabled-config:true}'.trim().equalsIgnoreCase('true')"
                + " && !'${tracker.finance.rh-daily-tracker.snapshot-cron:}'.trim().isEmpty()")
public class RobinhoodRhDailySnapshotScheduler {

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticService agenticService;
    private final RobinhoodRhDailyTrackerService dailyTrackerService;

    @EventListener(ApplicationReadyEvent.class)
    public void logSchedulerRegistration() {
        log.info(
                "RH Daily Tracker auto-capture scheduled: cron='{}' zone='{}'",
                dailyTrackerProps.snapshotCron(),
                dailyTrackerProps.snapshotZone());
    }

    @Scheduled(
            cron = "${tracker.finance.rh-daily-tracker.snapshot-cron}",
            zone = "${tracker.finance.rh-daily-tracker.snapshot-zone:America/Chicago}")
    public void captureDailySnapshots() {
        if (!dailyTrackerProps.snapshotSchedulerActive()) {
            return;
        }
        List<RobinhoodAgenticConnection> connections = connectionRepository.findAll();
        if (connections.isEmpty()) {
            log.debug("RH daily snapshot job: no Agentic connections");
            return;
        }
        Instant snapshotAt = Instant.now();
        log.info("RH daily snapshot job starting for {} connection(s) at {}", connections.size(), snapshotAt);
        for (RobinhoodAgenticConnection conn : connections) {
            if (!dailyTrackerService.isScheduledCaptureOwner(conn.getOwnerUserId())) {
                continue;
            }
            try {
                if (agenticProps.enabled() && agenticProps.serviceConfigured()) {
                    agenticService.syncConnectionBestEffort(conn);
                }
                dailyTrackerService.captureScheduledSnapshotsForOwner(conn.getOwnerUserId(), snapshotAt);
                log.info("RH daily snapshot ok for user {}", conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn("RH daily snapshot failed for user {}: {}", conn.getOwnerUserId(), e.getMessage());
            }
        }
    }
}
