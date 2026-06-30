package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhDailyDayNote;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhDailyDayNoteRepository extends JpaRepository<RobinhoodRhDailyDayNote, Long> {

    List<RobinhoodRhDailyDayNote> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            long ownerUserId, LocalDate from, LocalDate to);

    Optional<RobinhoodRhDailyDayNote> findByOwnerUserIdAndSnapshotDate(long ownerUserId, LocalDate snapshotDate);
}
