package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodCryptoTradingConnection;
import com.svp.tracker.finance.repository.RobinhoodCryptoTradingConnectionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Scheduled crypto portfolio snapshots via Robinhood Crypto Trading API. */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
        "'${tracker.finance.rh-crypto-tracker.snapshot-scheduler-enabled-config:true}'.trim().equalsIgnoreCase('true')"
                + " && !'${tracker.finance.rh-crypto-tracker.snapshot-cron:}'.trim().isEmpty()")
public class RobinhoodRhCryptoSnapshotScheduler {

    private final RobinhoodRhCryptoTrackerProperties cryptoTrackerProps;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodCryptoTradingConnectionRepository connectionRepository;
    private final RobinhoodRhCryptoTrackerService cryptoTrackerService;

    @EventListener(ApplicationReadyEvent.class)
    public void logSchedulerRegistration() {
        log.info("RH Crypto Tracker auto-capture scheduled: cron='{}'", cryptoTrackerProps.snapshotCron());
    }

    /** Invoked by admin cron scheduler ({@code finance.rh-crypto-tracker.snapshot}). */
    public void captureCryptoSnapshots() {
        if (!cryptoTrackerProps.snapshotSchedulerActive()) {
            return;
        }
        if (!agenticProps.serviceConfigured()) {
            log.debug("RH crypto snapshot job: sidecar not configured");
            return;
        }
        List<RobinhoodCryptoTradingConnection> connections = connectionRepository.findAllByOrderByOwnerUserIdAsc();
        if (connections.isEmpty()) {
            log.debug("RH crypto snapshot job: no crypto trading connections");
            return;
        }
        Instant snapshotAt = Instant.now();
        log.info("RH crypto snapshot job starting for {} connection(s) at {}", connections.size(), snapshotAt);
        for (RobinhoodCryptoTradingConnection conn : connections) {
            try {
                cryptoTrackerService.captureScheduledForOwner(conn.getOwnerUserId(), snapshotAt);
                log.info("RH crypto snapshot ok for user {}", conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn("RH crypto snapshot failed for user {}: {}", conn.getOwnerUserId(), e.getMessage());
            }
        }
    }
}
