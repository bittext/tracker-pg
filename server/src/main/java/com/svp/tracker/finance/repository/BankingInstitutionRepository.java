package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingInstitution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankingInstitutionRepository extends JpaRepository<BankingInstitution, Long> {

    @Query(
            """
            SELECT i FROM BankingInstitution i
            LEFT JOIN FETCH i.institutionType
            WHERE i.ownerUserId = :ownerUserId
            ORDER BY i.name ASC
            """)
    List<BankingInstitution> findByOwnerUserIdOrderByNameAsc(@Param("ownerUserId") long ownerUserId);

    @Query(
            """
            SELECT i FROM BankingInstitution i
            LEFT JOIN FETCH i.institutionType
            WHERE i.id = :id AND i.ownerUserId = :ownerUserId
            """)
    Optional<BankingInstitution> findByIdAndOwnerUserId(@Param("id") long id, @Param("ownerUserId") long ownerUserId);

    boolean existsByIdAndOwnerUserId(long id, long ownerUserId);

    boolean existsByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);

    Optional<BankingInstitution> findByOwnerUserIdAndNameIgnoreCase(long ownerUserId, String name);
}
