package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhDailySnapshotRepository extends JpaRepository<RobinhoodRhDailySnapshot, Long> {

    List<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
            long ownerUserId, LocalDate from, LocalDate to);

    Optional<RobinhoodRhDailySnapshot> findByOwnerUserIdAndSnapshotDateAndAccountSuffix(
            long ownerUserId, LocalDate snapshotDate, String accountSuffix);

    Optional<RobinhoodRhDailySnapshot> findTopByOwnerUserIdAndAccountSuffixAndSnapshotDateLessThanOrderBySnapshotDateDesc(
            long ownerUserId, String accountSuffix, LocalDate beforeDate);

    Optional<RobinhoodRhDailySnapshot> findByIdAndOwnerUserId(long id, long ownerUserId);
}
