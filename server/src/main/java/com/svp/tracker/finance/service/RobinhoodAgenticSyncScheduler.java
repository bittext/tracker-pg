package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Runs only when {@code tracker.finance.robinhood-agentic.sync-cron} is non-empty.
 * Avoids registering a placeholder cron (Spring 7 rejects e.g. Feb 31 as invalid one-time task).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("!'${tracker.finance.robinhood-agentic.sync-cron:}'.trim().isEmpty()")
public class RobinhoodAgenticSyncScheduler {

    private final RobinhoodAgenticProperties props;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticService agenticService;

    /** Invoked by admin cron scheduler ({@code finance.robinhood-agentic.sync}). */
    public void scheduledSync() {
        if (!props.enabled()) {
            return;
        }
        List<RobinhoodAgenticConnection> connections = connectionRepository.findAll();
        if (connections.isEmpty()) {
            return;
        }
        log.info("Robinhood Agentic scheduled sync starting for {} connection(s)", connections.size());
        for (RobinhoodAgenticConnection conn : connections) {
            try {
                agenticService.syncConnection(conn);
                log.info("Robinhood Agentic scheduled sync ok for user {}", conn.getOwnerUserId());
            } catch (Exception e) {
                log.warn(
                        "Robinhood Agentic scheduled sync failed for user {}: {}",
                        conn.getOwnerUserId(),
                        e.getMessage());
            }
        }
    }
}
