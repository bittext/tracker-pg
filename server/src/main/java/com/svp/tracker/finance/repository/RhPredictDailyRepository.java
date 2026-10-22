package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictDailyRepository extends JpaRepository<RhPredictDaily, Long> {
    Optional<RhPredictDaily> findByOwnerUserIdAndSnapshotDate(long ownerUserId, LocalDate snapshotDate);

    List<RhPredictDaily> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            long ownerUserId, LocalDate from, LocalDate to);
}
