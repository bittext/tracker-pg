package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.MarketsJourney;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketsJourneyRepository extends JpaRepository<MarketsJourney, Long> {
    List<MarketsJourney> findByOwnerUserIdOrderBySortOrderAscIdAsc(long ownerUserId);

    Optional<MarketsJourney> findByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndTitleIgnoreCase(long ownerUserId, String title);
}
