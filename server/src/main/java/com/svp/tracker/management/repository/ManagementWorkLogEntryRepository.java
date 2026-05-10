package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWorkLogEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementWorkLogEntryRepository extends JpaRepository<ManagementWorkLogEntry, Long> {

    List<ManagementWorkLogEntry> findByOwnerUserIdAndEntryDateBetweenOrderByEntryDateDescLoggedAtDesc(
            long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);

    List<ManagementWorkLogEntry> findByOwnerUserIdAndEntryDateOrderByLoggedAtDesc(long ownerUserId, LocalDate entryDate);

    @Query(
            "SELECT e.entryDate, COUNT(e) FROM ManagementWorkLogEntry e "
                    + "WHERE e.ownerUserId = :ownerId AND e.entryDate >= :from AND e.entryDate <= :to "
                    + "GROUP BY e.entryDate")
    List<Object[]> countByEntryDateInRange(
            @Param("ownerId") long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
