package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingInstitution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingInstitutionRepository extends JpaRepository<BankingInstitution, Long> {

    List<BankingInstitution> findByOwnerUserIdOrderByNameAsc(long ownerUserId);

    Optional<BankingInstitution> findByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);

    Optional<BankingInstitution> findByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);
}
