package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.TradingJournalEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradingJournalEntryRepository extends JpaRepository<TradingJournalEntry, Long> {

    Optional<TradingJournalEntry> findByOwnerUserIdAndSnapshotDate(long ownerUserId, LocalDate snapshotDate);

    Optional<TradingJournalEntry> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<TradingJournalEntry> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            long ownerUserId, LocalDate from, LocalDate to);

    @Query(
            value =
                    """
                    SELECT e.*
                    FROM trading_journal_entry e
                    LEFT JOIN trading_journal_ref r ON r.entry_id = e.id AND r.owner_user_id = e.owner_user_id
                    WHERE e.owner_user_id = :ownerUserId
                      AND e.snapshot_date BETWEEN :from AND :to
                      AND (
                        :q = ''
                        OR e.title ILIKE CONCAT('%', :q, '%')
                        OR e.body_markdown ILIKE CONCAT('%', :q, '%')
                        OR e.tags ILIKE CONCAT('%', :q, '%')
                        OR COALESCE(r.symbol, '') ILIKE CONCAT('%', :q, '%')
                        OR COALESCE(r.label, '') ILIKE CONCAT('%', :q, '%')
                        OR COALESCE(r.url, '') ILIKE CONCAT('%', :q, '%')
                      )
                    GROUP BY e.id
                    ORDER BY e.snapshot_date DESC
                    """,
            nativeQuery = true)
    List<TradingJournalEntry> search(
            @Param("ownerUserId") long ownerUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("q") String q);

    @Query(
            """
            SELECT e.snapshotDate FROM TradingJournalEntry e
            WHERE e.ownerUserId = :ownerUserId
              AND e.snapshotDate BETWEEN :from AND :to
            ORDER BY e.snapshotDate DESC
            """)
    List<LocalDate> findDates(
            @Param("ownerUserId") long ownerUserId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
