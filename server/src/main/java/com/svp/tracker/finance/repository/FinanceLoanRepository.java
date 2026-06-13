package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceLoan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceLoanRepository extends JpaRepository<FinanceLoan, Long> {
    List<FinanceLoan> findByOwnerUserIdOrderByInstitutionAscDateAvailedDesc(long ownerUserId);

    Optional<FinanceLoan> findByIdAndOwnerUserId(long id, long ownerUserId);
}
