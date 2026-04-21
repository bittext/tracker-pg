package com.svp.tracker.journal.repository;

import com.svp.tracker.journal.domain.JournalEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query(
            "SELECT e.loggedOn, COUNT(e) FROM JournalEntry e WHERE e.ownerUserId = :owner"
                    + " AND e.loggedOn >= :from AND e.loggedOn <= :to GROUP BY e.loggedOn")
    List<Object[]> countByDayInRange(
            @Param("owner") long ownerUserId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<JournalEntry> findByOwnerUserIdAndLoggedOnOrderByCreatedAtDesc(long ownerUserId, LocalDate loggedOn);

    /**
     * No fetch-join: DISTINCT + fetch + ORDER BY breaks on PostgreSQL/Hibernate. Tags load lazy inside
     * {@code @Transactional} on the service.
     */
    @Query(
            "SELECT e FROM JournalEntry e WHERE e.ownerUserId = :owner"
                    + " AND e.loggedOn >= :from AND e.loggedOn <= :to ORDER BY e.loggedOn DESC, e.createdAt DESC")
    List<JournalEntry> findRangeForOwner(
            @Param("owner") long ownerUserId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT e FROM JournalEntry e WHERE e.ownerUserId = :o AND e.loggedOn = :d ORDER BY e.createdAt DESC")
    List<JournalEntry> findDayForOwner(@Param("o") long ownerUserId, @Param("d") LocalDate day);

    @Query("SELECT e FROM JournalEntry e LEFT JOIN FETCH e.tags WHERE e.id = :id")
    java.util.Optional<JournalEntry> findByIdWithTags(@Param("id") long id);

    @Query("SELECT e FROM JournalEntry e LEFT JOIN FETCH e.tags LEFT JOIN FETCH e.attachments WHERE e.id = :id")
    java.util.Optional<JournalEntry> findByIdWithTagsAndAttachments(@Param("id") long id);
}
