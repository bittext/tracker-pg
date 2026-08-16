package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodRhDailySnapshotRepository extends JpaRepository<RobinhoodRhDailySnapshot, Long> {

    List<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
            long ownerUserId, LocalDate from, LocalDate to);

    Optional<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateAndAccountSuffixAndCaptureKind(
            long ownerUserId, LocalDate snapshotDate, String accountSuffix, String captureKind);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndCaptureKindOrderBySnapshotDateDesc(
            long ownerUserId, String accountSuffix, String captureKind);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndCaptureKindAndSnapshotDateLessThanOrderBySnapshotDateDesc(
            long ownerUserId, String accountSuffix, String captureKind, LocalDate beforeDate);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndSnapshotAtLessThanOrderBySnapshotAtDesc(
            long ownerUserId, String accountSuffix, Instant beforeAt);

    Optional<RobinhoodRhDailySnapshot> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotAtAndCaptureKind(
            long ownerUserId, Instant snapshotAt, String captureKind);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdOrderBySnapshotAtDescIdDesc(long ownerUserId);

    @Query("SELECT COUNT(s) FROM RobinhoodRhDailySnapshot s WHERE s.ownerUserId = :ownerUserId")
    long countByOwnerUserId(@Param("ownerUserId") long ownerUserId);

    /** Scheduled close totals only — avoids loading holdings_json / trades_json for lookback ranges. */
    @Query(
            """
            SELECT new com.svp.tracker.finance.dto.RhScheduledTotalRow(
                s.snapshotDate, s.accountSuffix, s.totalAccountValue)
            FROM RobinhoodRhDailySnapshot s
            WHERE s.ownerUserId = :ownerUserId
              AND s.snapshotDate BETWEEN :from AND :to
              AND s.captureKind = 'SCHEDULED'
            ORDER BY s.snapshotDate DESC, s.accountSuffix ASC
            """)
    List<RhScheduledTotalRow> findScheduledTotalsBetween(
            @Param("ownerUserId") long ownerUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Scheduled close totals for one account suffix, ascending by date (slap-point series). */
    @Query(
            """
            SELECT new com.svp.tracker.finance.dto.RhScheduledTotalRow(
                s.snapshotDate, s.accountSuffix, s.totalAccountValue)
            FROM RobinhoodRhDailySnapshot s
            WHERE s.ownerUserId = :ownerUserId
              AND s.accountSuffix = :accountSuffix
              AND s.captureKind = 'SCHEDULED'
            ORDER BY s.snapshotDate ASC
            """)
    List<RhScheduledTotalRow> findScheduledTotalsForSuffixAsc(
            @Param("ownerUserId") long ownerUserId, @Param("accountSuffix") String accountSuffix);
}
