package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhIndividualMarginAlertEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhIndividualMarginAlertEventRepository extends JpaRepository<RhIndividualMarginAlertEvent, Long> {

    List<RhIndividualMarginAlertEvent> findTop20ByOwnerUserIdAndAccountSuffixOrderByCreatedAtDesc(
            long ownerUserId, String accountSuffix);
}
