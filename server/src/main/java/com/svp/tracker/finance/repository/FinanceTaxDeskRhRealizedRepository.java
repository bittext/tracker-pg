package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTaxDeskRhRealized;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTaxDeskRhRealizedRepository extends JpaRepository<FinanceTaxDeskRhRealized, Long> {

    Optional<FinanceTaxDeskRhRealized> findByOwnerUserIdAndTaxYearAndAsOfDate(
            long ownerUserId, int taxYear, LocalDate asOfDate);

    Optional<FinanceTaxDeskRhRealized> findFirstByOwnerUserIdAndTaxYearAndAsOfDateLessThanEqualOrderByAsOfDateDesc(
            long ownerUserId, int taxYear, LocalDate asOfDate);
}
