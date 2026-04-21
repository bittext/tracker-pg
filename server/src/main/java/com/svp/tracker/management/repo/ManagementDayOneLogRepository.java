package com.svp.tracker.management.repo;

import com.svp.tracker.management.domain.ManagementDayOneLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementDayOneLogRepository
        extends JpaRepository<ManagementDayOneLog, Long>, JpaSpecificationExecutor<ManagementDayOneLog> {

    @EntityGraph(attributePaths = {"tags", "attachments"})
    @Override
    @org.springframework.lang.NonNull
    Optional<ManagementDayOneLog> findById(@org.springframework.lang.NonNull Long id);

    @Query(
            """
            SELECT e.loggedOn, COUNT(e)
            FROM ManagementDayOneLog e
            WHERE e.ownerUserId = :ownerId
              AND e.loggedOn >= :from
              AND e.loggedOn <= :to
            GROUP BY e.loggedOn
            """)
    List<Object[]> countGroupedByDayForOwner(
            @Param("ownerId") Long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(
            """
            SELECT e.loggedOn, COUNT(e)
            FROM ManagementDayOneLog e
            WHERE e.loggedOn >= :from
              AND e.loggedOn <= :to
            GROUP BY e.loggedOn
            """)
    List<Object[]> countGroupedByDayAll(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByOwnerUserIdAndLoggedOnBetween(Long ownerUserId, LocalDate from, LocalDate to);

    long countByOwnerUserIdAndLoggedOn(Long ownerUserId, LocalDate day);

    long countByLoggedOnBetween(LocalDate from, LocalDate to);

    long countByLoggedOn(LocalDate day);
}
