package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.MarketsJourneyEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketsJourneyEntryRepository extends JpaRepository<MarketsJourneyEntry, Long> {
    List<MarketsJourneyEntry> findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(long journeyId, long ownerUserId);

    Optional<MarketsJourneyEntry> findByIdAndOwnerUserId(long id, long ownerUserId);

    Optional<MarketsJourneyEntry> findByJourneyIdAndOwnerUserIdAndPeriodDate(
            long journeyId, long ownerUserId, LocalDate periodDate);

    void deleteByJourneyIdAndOwnerUserId(long journeyId, long ownerUserId);
}
