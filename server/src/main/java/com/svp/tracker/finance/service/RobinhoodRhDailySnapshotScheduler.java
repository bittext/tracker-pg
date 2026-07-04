package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Captures RH account snapshots hourly; official daily close at configured hour (default 9 PM Central). */
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
                "RH Daily Tracker auto-capture scheduled: cron='{}' zone='{}' closingHour={}",
                dailyTrackerProps.snapshotCron(),
                dailyTrackerProps.snapshotZone(),
                dailyTrackerProps.snapshotClosingHour());
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
        ZoneId zone = ZoneId.of(dailyTrackerProps.snapshotZone());
        int hour = ZonedDateTime.ofInstant(snapshotAt, zone).getHour();
        boolean closingHour = hour == dailyTrackerProps.snapshotClosingHour();
        log.info(
                "RH daily snapshot job starting for {} connection(s) at {} ({}closing hour)",
                connections.size(),
                snapshotAt,
                closingHour ? "" : "non-");
        for (RobinhoodAgenticConnection conn : connections) {
            if (!dailyTrackerService.isScheduledCaptureOwner(conn.getOwnerUserId())) {
                continue;
            }
            try {
                if (agenticProps.enabled() && agenticProps.serviceConfigured()) {
                    agenticService.syncConnectionBestEffort(conn);
                }
                if (closingHour) {
                    dailyTrackerService.captureScheduledSnapshotsForOwner(conn.getOwnerUserId(), snapshotAt);
                } else {
                    dailyTrackerService.captureIntradaySnapshotsForOwner(conn.getOwnerUserId(), snapshotAt);
                }
                log.info("RH daily snapshot ok for user {}", conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn("RH daily snapshot failed for user {}: {}", conn.getOwnerUserId(), e.getMessage());
            }
        }
    }
}
