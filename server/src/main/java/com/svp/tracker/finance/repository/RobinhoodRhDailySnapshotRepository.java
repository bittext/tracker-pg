package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhDailySnapshotRepository extends JpaRepository<RobinhoodRhDailySnapshot, Long> {

    List<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
            long ownerUserId, LocalDate from, LocalDate to);

    Optional<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateAndAccountSuffixAndCaptureKind(
            long ownerUserId, LocalDate snapshotDate, String accountSuffix, String captureKind);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndCaptureKindAndSnapshotDateLessThanOrderBySnapshotDateDesc(
            long ownerUserId, String accountSuffix, String captureKind, LocalDate beforeDate);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndSnapshotAtLessThanOrderBySnapshotAtDesc(
            long ownerUserId, String accountSuffix, Instant beforeAt);

    Optional<RobinhoodRhDailySnapshot> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotAtAndCaptureKind(
            long ownerUserId, Instant snapshotAt, String captureKind);
}
