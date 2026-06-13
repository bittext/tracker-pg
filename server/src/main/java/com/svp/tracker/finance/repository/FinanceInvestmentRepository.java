package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceInvestment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceInvestmentRepository extends JpaRepository<FinanceInvestment, Long> {
    List<FinanceInvestment> findByOwnerUserIdOrderByInstitutionAscNameAsc(long ownerUserId);

    Optional<FinanceInvestment> findByIdAndOwnerUserId(long id, long ownerUserId);
}
