package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCreditStanding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCreditStandingRepository extends JpaRepository<FinanceCreditStanding, Long> {
    Optional<FinanceCreditStanding> findByOwnerUserId(long ownerUserId);
}
