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

    /**
     * Entries in the date range that are linked to <strong>all</strong> of the given tags (AND semantics). The
     * subquery count matches the number of distinct required tags so the entry must have every tag, not a subset.
     */
    @Query(
            "SELECT e FROM JournalEntry e WHERE e.ownerUserId = :owner"
                    + " AND e.loggedOn >= :from AND e.loggedOn <= :to"
                    + " AND (SELECT COUNT(t.id) FROM e.tags t WHERE t.id IN :tagIds) = :expected"
                    + " ORDER BY e.loggedOn DESC, e.createdAt DESC")
    List<JournalEntry> findRangeForOwnerHavingAllSelectedTags(
            @Param("owner") long ownerUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("tagIds") java.util.Set<Long> tagIds,
            @Param("expected") long expectedTagCount);

    @Query("SELECT e FROM JournalEntry e WHERE e.ownerUserId = :o AND e.loggedOn = :d ORDER BY e.createdAt DESC")
    List<JournalEntry> findDayForOwner(@Param("o") long ownerUserId, @Param("d") LocalDate day);

    @Query("SELECT e FROM JournalEntry e LEFT JOIN FETCH e.tags WHERE e.id = :id")
    java.util.Optional<JournalEntry> findByIdWithTags(@Param("id") long id);

    @Query("SELECT e FROM JournalEntry e LEFT JOIN FETCH e.tags LEFT JOIN FETCH e.attachments WHERE e.id = :id")
    java.util.Optional<JournalEntry> findByIdWithTagsAndAttachments(@Param("id") long id);
}
