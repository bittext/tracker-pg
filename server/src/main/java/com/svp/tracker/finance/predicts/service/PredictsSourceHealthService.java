package com.svp.tracker.finance.predicts.service;

import com.svp.tracker.finance.predicts.domain.PredictsSource;
import com.svp.tracker.finance.predicts.domain.PredictsSourceHealth;
import com.svp.tracker.finance.predicts.repository.PredictsSourceHealthRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bookkeeping for the source-strip cards in the Predicts UI. Each call updates one row in
 * {@code finance_predicts_source_health}; rows are created lazily by source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictsSourceHealthService {

    private final PredictsSourceHealthRepository repository;

    @Transactional
    public void recordSuccess(PredictsSource source, int mentionsIngested) {
        PredictsSourceHealth row = upsertRow(source.wire());
        Instant now = Instant.now();
        row.setEnabled(true);
        row.setLastAttemptAt(now);
        row.setLastSuccessAt(now);
        row.setConsecutiveFailures(0);
        // Approximation: bump the 24h rolling counter optimistically; the nightly baseline job recomputes it.
        row.setMentionsIngested24h(Math.max(0, row.getMentionsIngested24h()) + Math.max(0, mentionsIngested));
        row.setUpdatedAt(now);
        repository.save(row);
    }

    @Transactional
    public void recordFailure(PredictsSource source, String errorMessage) {
        PredictsSourceHealth row = upsertRow(source.wire());
        Instant now = Instant.now();
        row.setEnabled(true);
        row.setLastAttemptAt(now);
        row.setLastErrorAt(now);
        row.setLastErrorMessage(trim(errorMessage, 500));
        row.setConsecutiveFailures(row.getConsecutiveFailures() + 1);
        row.setUpdatedAt(now);
        repository.save(row);
    }

    @Transactional
    public void recordDisabled(PredictsSource source) {
        PredictsSourceHealth row = upsertRow(source.wire());
        row.setEnabled(false);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    /** Read-side helper that returns all rows ordered by source name (alphabetic). */
    public List<PredictsSourceHealth> listAll() {
        return repository.findAll();
    }

    private PredictsSourceHealth upsertRow(String source) {
        return repository.findById(source).orElseGet(() -> {
            PredictsSourceHealth row = new PredictsSourceHealth();
            row.setSource(source);
            return row;
        });
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
