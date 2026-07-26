package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceFinvizEliteSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceFinvizEliteSnapshotRepository extends JpaRepository<FinanceFinvizEliteSnapshot, Long> {

    Optional<FinanceFinvizEliteSnapshot> findByCacheKey(String cacheKey);
}
