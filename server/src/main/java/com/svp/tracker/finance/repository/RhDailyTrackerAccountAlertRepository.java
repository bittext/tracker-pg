package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhDailyTrackerAccountAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhDailyTrackerAccountAlertRepository extends JpaRepository<RhDailyTrackerAccountAlert, Long> {

    List<RhDailyTrackerAccountAlert> findByOwnerUserIdOrderByAccountSuffixAsc(long ownerUserId);

    Optional<RhDailyTrackerAccountAlert> findByOwnerUserIdAndAccountSuffix(long ownerUserId, String accountSuffix);
}
