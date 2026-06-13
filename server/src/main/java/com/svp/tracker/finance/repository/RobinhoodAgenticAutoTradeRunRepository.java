package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAgenticAutoTradeRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAgenticAutoTradeRunRepository extends JpaRepository<RobinhoodAgenticAutoTradeRun, Long> {
    List<RobinhoodAgenticAutoTradeRun> findTop20ByOwnerUserIdOrderByStartedAtDesc(long ownerUserId);
}
