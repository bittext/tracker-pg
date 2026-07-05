package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhCryptoSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhCryptoSnapshotRepository extends JpaRepository<RobinhoodRhCryptoSnapshot, Long> {

    long countByOwnerUserId(long ownerUserId);

    List<RobinhoodRhCryptoSnapshot> findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescSnapshotAtDesc(
            long ownerUserId, LocalDate start, LocalDate end);
}
