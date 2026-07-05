package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhDailyTrackerAlertEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhDailyTrackerAlertEventRepository extends JpaRepository<RhDailyTrackerAlertEvent, Long> {

    List<RhDailyTrackerAlertEvent> findTop20ByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);
}
