package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingInstitutionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingInstitutionTypeRepository extends JpaRepository<BankingInstitutionType, Long> {

    List<BankingInstitutionType> findByOwnerUserIdOrderBySortOrderAscNameAsc(long ownerUserId);

    Optional<BankingInstitutionType> findByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);
}
