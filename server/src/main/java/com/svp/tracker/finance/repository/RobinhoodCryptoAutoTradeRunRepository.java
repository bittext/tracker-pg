package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodCryptoAutoTradeRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodCryptoAutoTradeRunRepository extends JpaRepository<RobinhoodCryptoAutoTradeRun, Long> {
    List<RobinhoodCryptoAutoTradeRun> findTop20ByOwnerUserIdOrderByStartedAtDesc(long ownerUserId);
}
