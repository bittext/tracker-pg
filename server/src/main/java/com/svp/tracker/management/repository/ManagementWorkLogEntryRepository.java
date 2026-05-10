package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementWorkLogEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementWorkLogEntryRepository extends JpaRepository<ManagementWorkLogEntry, Long> {

    @Query("SELECT DISTINCT e FROM ManagementWorkLogEntry e LEFT JOIN FETCH e.attachments WHERE e.id = :id")
    Optional<ManagementWorkLogEntry> findByIdWithAttachments(@Param("id") long id);

    @Query(
            "SELECT DISTINCT e FROM ManagementWorkLogEntry e LEFT JOIN FETCH e.attachments "
                    + "WHERE e.ownerUserId = :ownerId AND e.entryDate >= :from AND e.entryDate <= :to "
                    + "ORDER BY e.entryDate DESC, e.loggedAt DESC")
    List<ManagementWorkLogEntry> findByOwnerAndEntryDateBetweenWithAttachments(
            @Param("ownerId") long ownerId,
            @Param("from") LocalDate fromInclusive,
            @Param("to") LocalDate toInclusive);

    @Query(
            "SELECT DISTINCT e FROM ManagementWorkLogEntry e LEFT JOIN FETCH e.attachments "
                    + "WHERE e.ownerUserId = :ownerId AND e.entryDate = :date ORDER BY e.loggedAt DESC")
    List<ManagementWorkLogEntry> findByOwnerAndEntryDateWithAttachments(
            @Param("ownerId") long ownerId, @Param("date") LocalDate entryDate);

    @Query(
            "SELECT e.entryDate, COUNT(e) FROM ManagementWorkLogEntry e "
                    + "WHERE e.ownerUserId = :ownerId AND e.entryDate >= :from AND e.entryDate <= :to "
                    + "GROUP BY e.entryDate")
    List<Object[]> countByEntryDateInRange(
            @Param("ownerId") long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
