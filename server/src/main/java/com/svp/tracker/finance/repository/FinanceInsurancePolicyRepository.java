package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceInsurancePolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceInsurancePolicyRepository extends JpaRepository<FinanceInsurancePolicy, Long> {

    List<FinanceInsurancePolicy> findByOwnerUserIdOrderByCoverageEndDateAscCarrierAsc(long ownerUserId);

    Optional<FinanceInsurancePolicy> findByIdAndOwnerUserId(long id, long ownerUserId);
}
