package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceTaxDeskPayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTaxDeskPaymentRepository extends JpaRepository<FinanceTaxDeskPayment, Long> {

    List<FinanceTaxDeskPayment> findByOwnerUserIdAndTaxYearAndIgnoredFalseOrderByPaidOnAscIdAsc(
            long ownerUserId, int taxYear);

    Optional<FinanceTaxDeskPayment> findByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndTaxYearAndSourceAndSourceRef(
            long ownerUserId, int taxYear, String source, String sourceRef);
}
