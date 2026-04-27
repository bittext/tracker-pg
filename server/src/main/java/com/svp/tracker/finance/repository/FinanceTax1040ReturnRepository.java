package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTax1040Return;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTax1040ReturnRepository extends JpaRepository<FinanceTax1040Return, Long> {

    List<FinanceTax1040Return> findByOwnerUserIdOrderByTaxYearDesc(long ownerUserId);

    Optional<FinanceTax1040Return> findByOwnerUserIdAndTaxYear(long ownerUserId, int taxYear);
}
