package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RhPredictSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RhPredictSnapshotRepository extends JpaRepository<RhPredictSnapshot, Long> {
    Optional<RhPredictSnapshot> findByOwnerUserId(long ownerUserId);
}
