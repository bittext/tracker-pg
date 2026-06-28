package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhAccountStartingBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhAccountStartingBalanceRepository
        extends JpaRepository<RobinhoodRhAccountStartingBalance, Long> {

    List<RobinhoodRhAccountStartingBalance> findByOwnerUserIdOrderByAccountSuffixAsc(long ownerUserId);

    Optional<RobinhoodRhAccountStartingBalance> findByOwnerUserIdAndAccountSuffix(
            long ownerUserId, String accountSuffix);
}
