package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.dto.RobinhoodRhCryptoTrackerReportDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodRhCryptoSnapshotRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crypto holdings timeline for Reports → Crypto Tracker (separate from Daily Tracker). */
@Service
@RequiredArgsConstructor
public class RobinhoodRhCryptoTrackerService {

    public static final String STATUS_WAITING_FOR_MCP = "WAITING_FOR_MCP";
    public static final String STATUS_READY = "READY";

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodRhCryptoSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public RobinhoodRhCryptoTrackerReportDto buildReport(int year, List<Integer> months) {
        long ownerUserId = currentUser.requireUserId();
        boolean serviceConfigured = agenticProps.serviceConfigured();
        boolean connected = connectionRepository
                .findByOwnerUserId(ownerUserId)
                .filter(c -> c.getAccessToken() != null && !c.getAccessToken().isBlank())
                .isPresent();
        int snapshotCount = (int) snapshotRepository.countByOwnerUserId(ownerUserId);

        List<String> notes = buildNotes(serviceConfigured, connected, snapshotCount);

        return new RobinhoodRhCryptoTrackerReportDto(
                year,
                months == null || months.isEmpty() ? List.of() : List.copyOf(months),
                STATUS_WAITING_FOR_MCP,
                serviceConfigured,
                connected,
                false,
                snapshotCount,
                List.of(),
                notes);
    }

    private static List<String> buildNotes(boolean serviceConfigured, boolean connected, int snapshotCount) {
        List<String> notes = new ArrayList<>();
        notes.add(
                "Robinhood Agentic MCP does not expose crypto positions yet (equities and options only). "
                        + "This tab is reserved for crypto holdings and changes over time, kept separate from Daily Tracker.");
        if (!serviceConfigured) {
            notes.add("Robinhood Agentic sidecar is not configured on this server.");
        } else if (!connected) {
            notes.add("Connect Robinhood Agentic under Finance → Trading so captures can run once crypto sync is enabled.");
        } else if (snapshotCount == 0) {
            notes.add("Agentic is connected. Crypto snapshots will appear here after Robinhood enables crypto read tools.");
        }
        notes.add(
                "Planned: hourly and manual captures, coin-level holdings, and period-over-period value changes — "
                        + "without mixing crypto into brokerage Daily Tracker totals.");
        return List.copyOf(notes);
    }
}
