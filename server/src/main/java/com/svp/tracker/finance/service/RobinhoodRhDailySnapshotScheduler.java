package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Captures RH account snapshots daily at 9 PM Central (configurable). */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("!'${tracker.finance.rh-daily-tracker.snapshot-cron:}'.trim().isEmpty()")
public class RobinhoodRhDailySnapshotScheduler {

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticService agenticService;
    private final RobinhoodRhDailyTrackerService dailyTrackerService;

    @Scheduled(
            cron = "${tracker.finance.rh-daily-tracker.snapshot-cron}",
            zone = "${tracker.finance.rh-daily-tracker.snapshot-zone:America/Chicago}")
    public void captureDailySnapshots() {
        List<RobinhoodAgenticConnection> connections = connectionRepository.findAll();
        if (connections.isEmpty()) {
            return;
        }
        log.info("RH daily snapshot job starting for {} connection(s)", connections.size());
        for (RobinhoodAgenticConnection conn : connections) {
            try {
                if (agenticProps.enabled() && agenticProps.serviceConfigured()) {
                    agenticService.syncConnection(conn);
                }
                dailyTrackerService.captureScheduledSnapshotsForOwner(conn.getOwnerUserId(), java.time.Instant.now());
                log.info("RH daily snapshot ok for user {}", conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn("RH daily snapshot failed for user {}: {}", conn.getOwnerUserId(), e.getMessage());
            }
        }
    }
}
