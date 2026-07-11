package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhDailyTrackerAiInsight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhDailyTrackerAiInsightRepository extends JpaRepository<RhDailyTrackerAiInsight, Long> {

    Optional<RhDailyTrackerAiInsight> findByOwnerUserIdAndScopeAndPeriodKey(
            long ownerUserId, String scope, String periodKey);
}
