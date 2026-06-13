package com.svp.tracker.finance.service;

import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAgenticSyncScheduler {

    private final RobinhoodAgenticProperties props;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticService agenticService;

    @Scheduled(cron = "${tracker.finance.robinhood-agentic.sync-cron:0 0 0 31 2 ?}", zone = "UTC")
    public void scheduledSync() {
        if (!props.enabled() || !props.syncCronEnabled()) {
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
