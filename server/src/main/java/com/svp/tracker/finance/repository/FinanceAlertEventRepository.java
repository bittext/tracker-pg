package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceAlertEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceAlertEventRepository extends JpaRepository<FinanceAlertEvent, Long> {

    List<FinanceAlertEvent> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId, Pageable pageable);

    List<FinanceAlertEvent> findByAlertIdAndOwnerUserIdOrderByCreatedAtDesc(Long alertId, Long ownerUserId, Pageable pageable);
}
